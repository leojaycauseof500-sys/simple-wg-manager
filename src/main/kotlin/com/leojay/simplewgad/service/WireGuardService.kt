package com.leojay.simplewgad.service

import com.leojay.simplewgad.model.InterfaceConfig
import com.leojay.simplewgad.model.WireGuardStatus
import com.leojay.simplewgad.model.ServiceStatus
import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.util.CommandExecutor
import com.leojay.simplewgad.util.WgEntryParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class WireGuardService(
    private val commandExecutor: CommandExecutor,
    private val wgEntryParser: WgEntryParser,
    @Value("\${wg-manager.default.interface-name}") private val interfaceName : String,
    @Value("\${wg-manager.default.server-public-key}") private val serverPublicKey : String,
) {
    private val logger = LoggerFactory.getLogger(WireGuardService::class.java)

    fun checkWireGuardStatus(): WireGuardStatus {
        return try {
            val wgEntries = getWireGuardStatistics()

            WireGuardStatus(
                isProcessRunning = checkProcessRunning(wgEntries),
                interfaceName = getInterfaceName(wgEntries),
                totalStatus = checkTotalStatus(wgEntries)
            )
        } catch (e: Exception) {
            logger.error("Failed to check WireGuard status", e)
            WireGuardStatus(
                isProcessRunning = false,
                interfaceName = null,
                totalStatus = ServiceStatus.ERROR
            )
        }
    }

    /**
     * 获取 WireGuard 详细状态信息（使用 wg 命令）
     * 需要配置 sudoers 免密码权限
     */
    fun getWireGuardDetails(): String {
        val result = commandExecutor.runCommand("wg show all dump", 10)
        return if (result.exitCode == 0) {
            result.output
        } else {
            logger.error("Failed to get WireGuard details. Exit code: ${result.exitCode}, Error: ${result.errMsg}")
            ""
        }
    }

    /**
     * 获取 WireGuard 接口统计信息
     */
    fun getWireGuardStatistics(): List<WgEntry> {
        return wgEntryParser.parseWgDump(getWireGuardDetails().split('\n')).getOrThrow()
    }

    fun getInterfaceConfig() = InterfaceConfig(
        "", 0, emptyList(), null, emptyList()
    )
    /**
     * 获取服务器配置信息
     */
    fun getServerConfig(): ServerConfig {
        return try {
            // 获取接口配置
            val interfaceConfig = getInterfaceConfig()

            // 获取配置文件内容
            val configFileContent = getConfigFileContent("wg0")

            // 获取系统信息
            val systemInfo = getSystemInfo()

            ServerConfig(
                interfaceConfig = interfaceConfig,
                configFileContent = configFileContent,
                systemInfo = systemInfo,
                success = true
            )
        } catch (e: Exception) {
            logger.error("Failed to get server config", e)
            ServerConfig(
                interfaceConfig = null,
                configFileContent = null,
                systemInfo = null,
                success = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * 获取接口配置信息
     */
//    private fun getInterfaceConfig(): WgConfig.InterfaceConfig {
//        val wgEntries = getWireGuardStatistics()
//
//        val interfaceEntry = wgEntries.find { it is WgEntry.Interface } as? WgEntry.Interface
//        val peerEntries = wgEntries.filterIsInstance<WgEntry.Peer>()
//
//        return WgConfig.InterfaceConfig(
//            interfaceName = interfaceEntry?.name ?: "unknown",
//            privateKey = interfaceEntry?.publicKey ?: "unknown",
//            listenPort = interfaceEntry?.listenPort ?: 0,
//            peers = peerEntries.map { peer ->
//                WgConfig.PeerConfig(
//                    publicKey = peer.publicKey,
//                    endpoint = peer.endpoint,
//                    allowedIps = peer.allowedIps,
//                    latestHandshake = peer.latestHandshake,
//                    transferRx = peer.transferRx,
//                    transferTx = peer.transferTx
//                )
//            }
//        )
//    }

    /**
     * 获取配置文件内容
     */
    private fun getConfigFileContent(interfaceName: String): String? {
        val configPath = "/etc/wireguard/$interfaceName.conf"
        val result = commandExecutor.runCommand("sudo cat $configPath", 5)

        return if (result.exitCode == 0) {
            result.output
        } else {
            logger.warn("Failed to read config file at $configPath: ${result.errMsg}")
            null
        }
    }

    /**
     * 获取系统信息
     */
    private fun getSystemInfo(): SystemInfo {
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

        return SystemInfo(
            kernelVersion = kernelVersion,
            wireGuardVersion = wgVersion,
            uptime = uptime,
            ipInfo = ipInfo
        )
    }

    private fun checkProcessRunning(wgEntries : List<WgEntry>): Boolean {
        return wgEntries.isNotEmpty()
    }

    private fun getInterfaceName(wgEntries : List<WgEntry>): String {
        return wgEntries.fold(""){ acc, entry ->
                if (entry is WgEntry.Interface) "$acc ${entry.name}"
            else acc
        }
    }

    private fun checkTotalStatus(wgEntries : List<WgEntry>): ServiceStatus {
        when (wgEntries.size) {
            0 -> return ServiceStatus.ERROR
            else -> return ServiceStatus.RUNNING
        }
    }

    private fun determineTotalStatus(
        isKernelModuleLoaded: Boolean,
        isProcessRunning: Boolean
    ): ServiceStatus {
        return when {
            !isKernelModuleLoaded -> ServiceStatus.ERROR
            isProcessRunning -> ServiceStatus.RUNNING
            else -> ServiceStatus.STOPPED
        }
    }

    /**
     * 添加新客户端
     */
    fun addClient(clientName: String, allowedIps: String = "10.0.0.2/32"): ClientConfigResult {
        return try {
            // 1. 生成客户端公私钥对
            val keyPair = generateKeyPair()

            // 2. 获取服务器接口信息
            val interfaceConfig = getInterfaceConfig()
            val serverPublicKey = serverPublicKey
            val serverEndpoint = getServerEndpoint()

            // 3. 生成客户端配置
            val clientConfig = generateClientConfig(
                clientName = clientName,
                privateKey = keyPair.privateKey,
                serverPublicKey = serverPublicKey,
                serverEndpoint = serverEndpoint,
                allowedIps = allowedIps
            )

            // 4. 更新服务器配置文件
            val updateResult = updateServerConfig(
                clientPublicKey = keyPair.publicKey,
                allowedIps = allowedIps
            )

            if (!updateResult.success) {
                return ClientConfigResult(
                    success = false,
                    errorMessage = "更新服务器配置失败: ${updateResult.errorMessage}"
                )
            }

            // 5. 重启 WireGuard 服务使配置生效
            restartWireGuardService()

            ClientConfigResult(
                success = true,
                clientName = clientName,
                clientConfig = clientConfig,
                publicKey = keyPair.publicKey,
                privateKey = keyPair.privateKey,
                qrCode = generateQRCode(clientConfig)
            )
        } catch (e: Exception) {
            logger.error("添加客户端失败", e)
            ClientConfigResult(
                success = false,
                errorMessage = "添加客户端失败: ${e.message}"
            )
        }
    }

    /**
     * 生成 WireGuard 密钥对
     */
    private fun generateKeyPair(): KeyPair {
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

        return KeyPair(privateKey, publicKey)
    }

    /**
     * 获取服务器端点（IP地址和端口）
     */
    private fun getServerEndpoint(): String {
        // 尝试获取公网IP
        val publicIpResult = commandExecutor.runCommand("curl -s ifconfig.me", 10)
        val publicIp = if (publicIpResult.exitCode == 0 && publicIpResult.output.isNotBlank()) {
            publicIpResult.output.trim()
        } else {
            // 如果获取公网IP失败，使用本地IP
            val localIpResult = commandExecutor.runCommand("hostname -I | awk '{print \$1}'", 5)
            if (localIpResult.exitCode == 0 && localIpResult.output.isNotBlank()) {
                localIpResult.output.trim()
            } else {
                "YOUR_SERVER_IP"
            }
        }

        // 获取监听端口
        val interfaceConfig = getInterfaceConfig()
        val port = interfaceConfig.listenPort

        return "$publicIp:$port"
    }

    /**
     * 生成客户端配置文件
     */
    private fun generateClientConfig(
        clientName: String,
        privateKey: String,
        serverPublicKey: String,
        serverEndpoint: String,
        allowedIps: String
    ): String {
        return """
            # WireGuard 客户端配置 - $clientName
            # 生成时间: ${java.time.LocalDateTime.now()}

            [Interface]
            PrivateKey = $privateKey
            Address = ${getNextClientIp()}
            DNS = 8.8.8.8, 1.1.1.1

            [Peer]
            PublicKey = $serverPublicKey
            Endpoint = $serverEndpoint
            AllowedIPs = $allowedIps
            PersistentKeepalive = 25
        """.trimIndent()
    }

    /**
     * 获取下一个可用的客户端IP地址
     */
    //todo : 需要重写
    private fun getNextClientIp(): String {
//        val interfaceConfig = getInterfaceConfig()
//        val existingIps = interfaceConfig.peers.flatMap { it.allowedIps }
//
//        // 简单的IP分配逻辑：从 10.0.0.2 开始递增
//        var ipIndex = 2
//        while (true) {
//            val candidateIp = "10.0.0.$ipIndex/32"
//            if (!existingIps.contains(candidateIp)) {
//                return candidateIp.replace("/32", "")
//            }
//            ipIndex++
//            if (ipIndex > 254) {
//                throw RuntimeException("没有可用的IP地址")
//            }
//        }
        return "10.0.0.3"
    }

    /**
     * 更新服务器配置文件
     */
    private fun updateServerConfig(clientPublicKey: String, allowedIps: String): UpdateResult {
        val interfaceConfig = getInterfaceConfig()
        val configPath = "/etc/wireguard/${interfaceName}.conf"

        // 读取现有配置
        val readResult = commandExecutor.runCommand("cat $configPath", 5)
        if (readResult.exitCode != 0) {
            return UpdateResult(false, "读取配置文件失败: ${readResult.errMsg}")
        }

        val existingConfig = readResult.output

        // 添加新的Peer配置
        val newPeerConfig = """

            [Peer]
            # 客户端: ${java.time.LocalDateTime.now()}
            PublicKey = $clientPublicKey
            AllowedIPs = $allowedIps
        """.trimIndent()

        val updatedConfig = existingConfig + newPeerConfig

        // 写入新配置
        val tempFile = "/tmp/wg_${System.currentTimeMillis()}.conf"
        val writeTempResult = commandExecutor.runCommand("echo '${updatedConfig.replace("'", "'\"'\"'")}' > $tempFile", 5)
        if (writeTempResult.exitCode != 0) {
            return UpdateResult(false, "创建临时文件失败: ${writeTempResult.errMsg}")
        }

        // 复制到配置文件
        val copyResult = commandExecutor.runCommand("cp $tempFile $configPath", 5)
        if (copyResult.exitCode != 0) {
            return UpdateResult(false, "复制配置文件失败: ${copyResult.errMsg}")
        }

        // 清理临时文件
        commandExecutor.runCommand("rm -f $tempFile", 5)

        return UpdateResult(true, null)
    }

    /**
     * 重启 WireGuard 服务
     */
    private fun restartWireGuardService() {
        val interfaceConfig = getInterfaceConfig()
        commandExecutor.runCommand("wg-quick down ${interfaceName}", 10)
        commandExecutor.runCommand("wg-quick up ${interfaceName}", 10)
    }

    /**
     * 生成二维码（简单实现）
     */
    private fun generateQRCode(config: String): String {
        // 这里可以集成二维码生成库
        // 暂时返回空字符串，后续可以集成
        return ""
    }
}

/**
 * 服务器配置数据类
 */
data class ServerConfig(
    val interfaceConfig: InterfaceConfig?,
    val configFileContent: String?,
    val systemInfo: SystemInfo?,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * 系统信息数据类
 */
data class SystemInfo(
    val kernelVersion: String,
    val wireGuardVersion: String,
    val uptime: String,
    val ipInfo: String
)

/**
 * 客户端配置结果
 */
data class ClientConfigResult(
    val success: Boolean,
    val clientName: String? = null,
    val clientConfig: String? = null,
    val publicKey: String? = null,
    val privateKey: String? = null,
    val qrCode: String? = null,
    val errorMessage: String? = null
)

/**
 * 密钥对
 */
data class KeyPair(
    val privateKey: String,
    val publicKey: String
)

/**
 * 更新结果
 */
data class UpdateResult(
    val success: Boolean,
    val errorMessage: String?
)
