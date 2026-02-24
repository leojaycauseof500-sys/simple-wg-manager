package com.leojay.simplewgad.service.impl

import com.leojay.simplewgad.model.InterfaceConfig
import com.leojay.simplewgad.model.PeerConfig
import com.leojay.simplewgad.model.WireGuardStatus
import com.leojay.simplewgad.model.ServiceStatus
import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.service.*
import com.leojay.simplewgad.util.CommandExecutor
import com.leojay.simplewgad.util.WgConfigEncoder
import com.leojay.simplewgad.util.WgEntryParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class WireGuardServiceImpl(
    private val commandExecutor: CommandExecutor,
    private val wgEntryParser: WgEntryParser,
    private val wgConfigEncoder: WgConfigEncoder,
    @Value("\${wg-manager.default.interface-name}") private val interfaceName: String,
    @Value("\${wg-manager.default.server-public-key}") private val serverPublicKey: String,
) : WireGuardService {

    private val logger = LoggerFactory.getLogger(WireGuardServiceImpl::class.java)

    override fun checkWireGuardStatus(): Result<WireGuardStatus> {
        return runCatching {
            val wgEntries = getWireGuardStatistics().getOrThrow()

            WireGuardStatus(
                isProcessRunning = checkProcessRunning(wgEntries),
                interfaceName = getInterfaceName(wgEntries),
                totalStatus = checkTotalStatus(wgEntries)
            )
        }
    }

    override fun getWireGuardDetails(): Result<String> {
        return runCatching {
            val result = commandExecutor.runCommand("wg show all dump", 10)
            if (result.exitCode == 0) {
                result.output
            } else {
                throw RuntimeException("Failed to get WireGuard details. Exit code: ${result.exitCode}")
            }
        }
    }

    override fun getWireGuardStatistics(): Result<List<WgEntry>> {
        return runCatching {
            val details = getWireGuardDetails().getOrThrow()
            wgEntryParser.parseWgDump(details.split('\n')).getOrThrow()
        }
    }

    override fun getInterfaceConfig(): Result<InterfaceConfig> {
        return runCatching {
            // TODO: 实现从实际配置文件中解析接口配置
            // 暂时返回默认配置
            InterfaceConfig(
                privateKey = "",
                listenPort = 51820,
                address = emptyList(),
                peers = emptyList()
            )
        }
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

            // 1. 生成客户端公私钥对
            val keyPair = generateKeyPair().getOrThrow()

            // 2. 获取服务器端点
            val serverEndpoint = getServerEndpoint().getOrThrow()

            // 3. 生成客户端配置
            val clientConfig = wgConfigEncoder.generateClientConfig(
                clientName = clientName,
                privateKey = keyPair.privateKey,
                serverPublicKey = serverPublicKey,
                serverEndpoint = serverEndpoint,
                allowedIps = allowedIps,
                dnsServers = listOf("8.8.8.8", "1.1.1.1")
            ).getOrThrow()

            // 4. 更新服务器配置文件
            val newPeer = PeerConfig(
                publicKey = keyPair.publicKey,
                allowedIPs = listOf(allowedIps)
            )

            val existingConfig = getConfigFileContent(interfaceName).getOrThrow()
            val updatedConfig = wgConfigEncoder.updateServerConfig(existingConfig, newPeer).getOrThrow()

            // 5. 写入新配置
            writeConfigFile(interfaceName, updatedConfig).getOrThrow()

            // 6. 重启服务使配置生效
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
            commandExecutor.runCommand("wg-quick down $interfaceName", 10)
            commandExecutor.runCommand("wg-quick up $interfaceName", 10)
        }
    }

    override fun getConfigFileContent(interfaceName: String): Result<String> {
        return runCatching {
            val configPath = "/etc/wireguard/$interfaceName.conf"
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
            val interfaceConfig = getInterfaceConfig().getOrThrow()
            val port = interfaceConfig.listenPort

            "$publicIp:$port"
        }
    }

    private fun writeConfigFile(interfaceName: String, config: String): Result<Unit> {
        return runCatching {
            val configPath = "/etc/wireguard/$interfaceName.conf"
            val tempFile = "/tmp/wg_${System.currentTimeMillis()}.conf"

            // 写入临时文件
            val writeResult = commandExecutor.runCommand("echo '${config.replace("'", "'\"'\"'")}' > $tempFile", 5)
            if (writeResult.exitCode != 0) {
                throw RuntimeException("创建临时文件失败: ${writeResult.errMsg}")
            }

            // 复制到配置文件
            val copyResult = commandExecutor.runCommand("sudo cp $tempFile $configPath", 5)
            if (copyResult.exitCode != 0) {
                throw RuntimeException("复制配置文件失败: ${copyResult.errMsg}")
            }

            // 设置权限
            commandExecutor.runCommand("sudo chmod 600 $configPath", 5)

            // 清理临时文件
            commandExecutor.runCommand("rm -f $tempFile", 5)
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
