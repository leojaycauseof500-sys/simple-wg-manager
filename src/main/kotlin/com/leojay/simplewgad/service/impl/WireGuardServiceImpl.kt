package com.leojay.simplewgad.service.impl

import com.leojay.simplewgad.model.ClientMetaData
import com.leojay.simplewgad.model.InterfaceConfig
import com.leojay.simplewgad.model.PeerConfig
import com.leojay.simplewgad.model.WireGuardStatus
import com.leojay.simplewgad.model.ServiceStatus
import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.repository.PeerMetaDataRepository
import com.leojay.simplewgad.service.*
import com.leojay.simplewgad.util.CommandExecutor
import com.leojay.simplewgad.util.WgConfigEncoder
import com.leojay.simplewgad.util.WgEntryParser
import com.leojay.simplewgad.util.constant.Symbols
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime

@Service
class WireGuardServiceImpl(
    private val commandExecutor: CommandExecutor,
    private val wgEntryParser: WgEntryParser,
    private val wgConfigEncoder: WgConfigEncoder,
    private val peersMetaDataRepository: PeerMetaDataRepository,
    @Value("\${wg-manager.peer-meta-data.path}") private val peerMetaDataPath: String,
    @Value("\${wg-manager.default.interface-name}") private val interfaceName: String,
    @Value("\${wg-manager.default.server-private-key}") private val serverPrivateKey: String,
    @Value("\${wg-manager.default.server-ip}") private val serverIP: String,
    @Value("\${wg-manager.default.listen-port}") private val serverPort: String,
) : WireGuardService {

    private val logger = LoggerFactory.getLogger(WireGuardServiceImpl::class.java)
    private val configPath = "$peerMetaDataPath$interfaceName.conf"
    private val peersMetaData = peersMetaDataRepository.getMaskedData()
    private val serverPublicKey = peersMetaData.server.publicKey

    override fun checkWireGuardStatus(): Result<WireGuardStatus> {
        return getWireGuardStatistics()
            .recoverCatching { exception ->
                // 第一次失败，尝试重启服务
                restartWireGuardService().getOrThrow()
                // 重启成功后，进行第二次尝试
                getWireGuardStatistics().getOrThrow()
            }
            .map { wgEntries ->
                WireGuardStatus(
                    isProcessRunning = checkProcessRunning(wgEntries),
                    interfaceName = getInterfaceName(wgEntries),
                    totalStatus = checkTotalStatus(wgEntries)
                )
            }
    }

    override fun getWireGuardDetails(): Result<String> =
        commandExecutor.runCommand("wg show all dump", 10).let { result ->
            if (result.exitCode == 0) Result.success(result.output)
            else Result.failure(RuntimeException("Exit code: ${result.exitCode}"))
        }


    override fun getWireGuardStatistics(): Result<List<WgEntry>> =
        getWireGuardDetails()
            .map { it.split('\n') }
            .mapCatching { lines ->
                wgEntryParser.parseWgDump(lines).getOrThrow()
            }

    override fun getInterfaceConfig(): Result<InterfaceConfig> =
        Result.success(configPath)
            .mapCatching { path ->
                readSpecificInterfaceConfig(path) ?: throw RuntimeException("Config not found at $path")
            }

    /**
     * 读取特定接口的配置文件
     */
    private fun readSpecificInterfaceConfig(configPath : String): InterfaceConfig? {
            logger.debug("尝试读取特定配置文件: $configPath")

            val result = commandExecutor.runCommand("sudo cat $configPath", 5)
        //todo 这里逻辑似乎有点问题 o_O?但能跑
            if (result.exitCode != 0) {
                //不存在配置文件就用元数据生成一份配置写入
                return peersMetaDataRepository.getUnmaskedData().toInterfaceConfig().also {
                    logger.warn("配置文件不存在或无法读取: ${result.errMsg}, 从元数据中重新生成一份$it")
                    saveConfig(it)
                }.copy(privateKey = "******")
            }

            val configContent = result.output
            if (configContent.isBlank()) {
                logger.warn("配置文件内容为空")
                return null
            }

            logger.debug("成功读取配置文件内容，长度: ${configContent.length}")
            return parseConfigContent(configContent)
    }

    private fun parseConfigContent(configContent: String): InterfaceConfig {
        logger.debug("开始解析配置内容")

        val parseResult = wgEntryParser.parseConfigFile(configContent)
        if (parseResult.isFailure) {
            logger.error("解析配置文件失败: ${parseResult.exceptionOrNull()?.message}")
            throw RuntimeException("解析配置文件失败: ${parseResult.exceptionOrNull()?.message}")
        }

        val interfaceConfig = parseResult.getOrThrow()
        logger.info("成功解析接口配置: peers=${interfaceConfig.peers.size}, address=${interfaceConfig.address}")

        return interfaceConfig
    }


    override fun getServerConfig(): Result<ServerConfig> {
        return runCatching {
            // 获取接口配置
            val interfaceConfig = getInterfaceConfig().getOrThrow()

            // 获取配置文件内容
            val configFileContent = getConfigFileContent(interfaceName).getOrNull()

            // 获取系统信息
            val systemInfo = getSystemInfo().getOrThrow()

            ServerConfig(
                interfaceName = interfaceName,
                interfaceConfig = interfaceConfig,
                configFileContent = configFileContent,
                systemInfo = systemInfo
            )
        }
    }

    override fun addClient(clientName: String, allowedIps: String): Result<ClientConfigResult> {
        return runCatching {
            require(clientName.isNotBlank()) { "客户端名称不能为空" }
            require(allowedIps.isNotBlank()) { "AllowedIPs 不能为空" }

            val keyPair = generateKeyPair().getOrThrow()

            val clientConfig = wgConfigEncoder.generateClientConfig(
                clientName = clientName,
                privateKey = keyPair.privateKey,
                serverPublicKey = serverPublicKey,
                serverEndpoint = getServerEndpoint().getOrThrow(),
                allowedIps = allowedIps,
                dnsServers = null
            ).getOrThrow()

            val interfaceConfig = getInterfaceConfig().getOrThrow().apply {
                peers.add(PeerConfig(
                    publicKey = keyPair.publicKey,
                    presharedKey = null,
                    allowedIPs = allowedIps.split(Symbols.COMMA)
                ))
            }
            saveConfig(interfaceConfig)


            peersMetaDataRepository.addClientMetaData(
                ClientMetaData(
                    name = clientName,
                    privateKey = keyPair.privateKey,
                    publicKey = keyPair.publicKey,
                    address = allowedIps,
                    enabled = true
                )
            )
            // 重启服务使配置生效
            restartWireGuardService().getOrThrow()

            ClientConfigResult(
                clientName = clientName,
                clientConfig = clientConfig,
                publicKey = keyPair.publicKey,
                privateKey = keyPair.privateKey
            )
        }
    }

    override fun restartWireGuardService(): Result<Unit> {
        return runCatching {
            //如果没有配置文件，从元数据读取一份再保存
            readSpecificInterfaceConfig(configPath) ?: run {
                saveConfig(peersMetaDataRepository.getUnmaskedData().toInterfaceConfig())
            }

            commandExecutor.runCommand("wg-quick down $configPath", 10)
            commandExecutor.runCommand("wg-quick up $configPath", 10)
        }
    }

    override fun getConfigFileContent(interfaceName: String): Result<String> {
        return runCatching {
            val result = commandExecutor.runCommand("sudo cat $configPath", 5)

            if (result.exitCode == 0) {
                result.output
            } else {
                throw RuntimeException("读取配置文件失败: ${result.errMsg}")
            }
        }
    }

    override fun getSystemInfo(): Result<SystemInfo> {
        return runCatching {
            // 获取内核版本
            val kernelResult = commandExecutor.runCommand("uname -r", 5)
            val kernelVersion = if (kernelResult.exitCode == 0) kernelResult.output.trim() else "unknown"

            // 获取 WireGuard 版本
            val wgVersionResult = commandExecutor.runCommand("wg --version", 5)
            val wgVersion = if (wgVersionResult.exitCode == 0) wgVersionResult.output.trim() else "unknown"

            // 获取系统负载
            val uptimeResult = commandExecutor.runCommand("uptime", 5)
            val uptime = if (uptimeResult.exitCode == 0) uptimeResult.output.trim() else "unknown"

            // 获取网络接口信息
            val ipResult = commandExecutor.runCommand("ip addr show", 5)
            val ipInfo = if (ipResult.exitCode == 0) ipResult.output else "unknown"

            SystemInfo(
                kernelVersion = kernelVersion,
                wireGuardVersion = wgVersion,
                uptime = uptime,
                ipInfo = ipInfo
            )
        }
    }

    override fun deleteClient(clientUuid: String): Result<Unit> {
        return runCatching {
            // 从元数据中获取客户端信息
            val unmaskedData = peersMetaDataRepository.getUnmaskedData()
            val clientMeta = unmaskedData.clients[clientUuid]
                ?: throw RuntimeException("找不到 UUID 为 $clientUuid 的客户端")
            
            // 从接口配置中移除对应的 Peer
            val interfaceConfig = getInterfaceConfig().getOrThrow()
            val updatedPeers = interfaceConfig.peers.filterNot { peer ->
                peer.publicKey == clientMeta.publicKey
            }.toMutableList()
            
            val updatedInterfaceConfig = interfaceConfig.copy(peers = updatedPeers)
            
            // 保存更新后的配置
            saveConfig(updatedInterfaceConfig).getOrThrow()
            
            // 从元数据中删除客户端
            val deleted = peersMetaDataRepository.deleteClientMetaData(clientUuid)
            if (!deleted) {
                throw RuntimeException("从元数据中删除客户端失败")
            }
            
            // 重启服务使配置生效
            restartWireGuardService().getOrThrow()
        }
    }

    override fun saveConfig(interfaceConfig: InterfaceConfig): Result<Unit> {
        return runCatching {
            val updatedConfig = wgConfigEncoder.encodeToConfig(interfaceConfig).getOrThrow()
            writeConfigFile(interfaceName, updatedConfig).getOrThrow()
        }
    }

    override fun getClientConfig(clientUuid: String): Result<ClientConfigResult> {
        return runCatching {
            // 从元数据中获取客户端信息
            val unmaskedData = peersMetaDataRepository.getUnmaskedData()
            val clientMeta = unmaskedData.clients[clientUuid]
                ?: throw RuntimeException("找不到 UUID 为 $clientUuid 的客户端")
            
            // 获取服务器端点
            val serverEndpoint = getServerEndpoint().getOrThrow()
            
            // 生成客户端配置
            val clientConfig = wgConfigEncoder.generateClientConfig(
                clientName = clientMeta.name,
                privateKey = clientMeta.privateKey,
                serverPublicKey = peersMetaData.server.publicKey,
                serverEndpoint = serverEndpoint,
                allowedIps = clientMeta.address,
                dnsServers = listOf("8.8.8.8", "1.1.1.1") // 默认 DNS 服务器
            ).getOrThrow()
            
            ClientConfigResult(
                clientName = clientMeta.name,
                clientConfig = clientConfig,
                publicKey = clientMeta.publicKey,
                privateKey = clientMeta.privateKey
            )
        }
    }
    // ============ 私有方法 ============

    private fun checkProcessRunning(wgEntries: List<WgEntry>): Boolean {
        return wgEntries.isNotEmpty()
    }

    private fun getInterfaceName(wgEntries: List<WgEntry>): String {
        return wgEntries.fold("") { acc, entry ->
            if (entry is WgEntry.Interface) "$acc ${entry.name}"
            else acc
        }.trim()
    }

    private fun checkTotalStatus(wgEntries: List<WgEntry>): ServiceStatus {
        return when {
            wgEntries.isEmpty() -> ServiceStatus.ERROR
            else -> ServiceStatus.RUNNING
        }
    }

    private fun generateKeyPair(): Result<KeyPair> {
        return runCatching {
            // 生成私钥
            val privateKeyResult = commandExecutor.runCommand("wg genkey", 5)
            if (privateKeyResult.exitCode != 0) {
                throw RuntimeException("生成私钥失败: ${privateKeyResult.errMsg}")
            }
            val privateKey = privateKeyResult.output.trim()

            // 从私钥生成公钥
            val publicKeyResult = commandExecutor.runCommand("echo '$privateKey' | sudo wg pubkey", 5)
            if (publicKeyResult.exitCode != 0) {
                throw RuntimeException("生成公钥失败: ${publicKeyResult.errMsg}")
            }
            val publicKey = publicKeyResult.output.trim()

            KeyPair(privateKey, publicKey)
        }
    }

    private fun getServerEndpoint(): Result<String> {
        return runCatching {
            "$serverIP:$serverPort"
        }
    }

    private fun writeConfigFile(interfaceName: String, configContent: String): Result<Unit> {
        return runCatching {
            val configPath = "$peerMetaDataPath$interfaceName.conf"
            val backupPath = "$peerMetaDataPath$interfaceName-${LocalDateTime.now()}.conf.bak"

            val configFile = File(configPath).apply {
                parentFile?.mkdirs()
                if (!exists()) createNewFile()
            }
            //备份
            File(backupPath).let {
                if (!it.exists()) it.createNewFile()
                it.writeText(configFile.readText())
            }

            configFile.writeText(configContent)
        }
    }
}

/**
 * 密钥对
 */
data class KeyPair(
    val privateKey: String,
    val publicKey: String
)
