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

    private fun checkKernelModule(): Boolean {
        val result = commandExecutor.runCommand("lsmod | grep -q wireguard", 5)
        return result.exitCode == 0
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
