package com.leojay.simplewgad.service

import com.leojay.simplewgad.model.WireGuardStatus
import com.leojay.simplewgad.model.ServiceStatus
import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.util.CommandExecutor
import com.leojay.simplewgad.util.WgEntryParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WireGuardService(
    private val commandExecutor: CommandExecutor,
    private val wgEntryParser: WgEntryParser
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
        return wgEntryParser.parseWgDump(getWireGuardDetails().split('\n'))
    }

    /**
     * 获取服务器配置信息
     */
    fun getServerConfig(): ServerConfig {
        return try {
            // 获取接口配置
            val interfaceConfig = getInterfaceConfig()
            
            // 获取配置文件内容
            val configFileContent = getConfigFileContent(interfaceConfig.interfaceName)
            
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
    private fun getInterfaceConfig(): InterfaceConfig {
        val wgEntries = getWireGuardStatistics()
        
        val interfaceEntry = wgEntries.find { it is WgEntry.Interface } as? WgEntry.Interface
        val peerEntries = wgEntries.filterIsInstance<WgEntry.Peer>()
        
        return InterfaceConfig(
            interfaceName = interfaceEntry?.name ?: "unknown",
            publicKey = interfaceEntry?.publicKey ?: "unknown",
            listenPort = interfaceEntry?.listenPort ?: 0,
            peerCount = peerEntries.size,
            peers = peerEntries.map { peer ->
                PeerInfo(
                    publicKey = peer.publicKey,
                    endpoint = peer.endpoint,
                    allowedIps = peer.allowedIps,
                    latestHandshake = peer.latestHandshake,
                    transferRx = peer.transferRx,
                    transferTx = peer.transferTx
                )
            }
        )
    }
    
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
 * 接口配置数据类
 */
data class InterfaceConfig(
    val interfaceName: String,
    val publicKey: String,
    val listenPort: Int,
    val peerCount: Int,
    val peers: List<PeerInfo>
)

/**
 * 对等端信息数据类
 */
data class PeerInfo(
    val publicKey: String,
    val endpoint: String,
    val allowedIps: List<String>,
    val latestHandshake: Long,
    val transferRx: Long,
    val transferTx: Long
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
