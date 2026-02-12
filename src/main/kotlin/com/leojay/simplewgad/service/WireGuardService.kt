package com.leojay.simplewgad.service

import com.leojay.simplewgad.model.WireGuardStatus
import com.leojay.simplewgad.model.ServiceStatus
import com.leojay.simplewgad.util.CommandExecutor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class WireGuardService(
    private val commandExecutor: CommandExecutor
) {
    private val logger = LoggerFactory.getLogger(WireGuardService::class.java)
    
    fun checkWireGuardStatus(): WireGuardStatus {
        return try {
            // 检查内核模块是否加载
            val isKernelModuleLoaded = checkKernelModule()
            
            // 检查 wg-quick 进程是否运行
            val isProcessRunning = checkProcessRunning()
            
            // 检查接口状态
            val interfaceName = getInterfaceName()
            
            // 确定总体状态
            val totalStatus = determineTotalStatus(isKernelModuleLoaded, isProcessRunning)
            
            WireGuardStatus(
                isKernelModuleLoaded = isKernelModuleLoaded,
                isProcessRunning = isProcessRunning,
                interfaceName = interfaceName,
                totalStatus = totalStatus
            )
        } catch (e: Exception) {
            logger.error("Failed to check WireGuard status", e)
            WireGuardStatus(
                isKernelModuleLoaded = false,
                isProcessRunning = false,
                interfaceName = null,
                totalStatus = ServiceStatus.ERROR
            )
        }
    }
    
    private fun checkKernelModule(): Boolean {
        val result = commandExecutor.runCommand("lsmod | grep -q wireguard", 5)
        return result.exitCode == 0
    }
    
    private fun checkProcessRunning(): Boolean {
        val result = commandExecutor.runCommand("pgrep -x wg-quick", 5)
        return result.exitCode == 0
    }
    
    private fun getInterfaceName(): String? {
        val result = commandExecutor.runCommand("ip -o link show type wireguard | head -1 | awk -F': ' '{print \$2}'", 5)
        return if (result.exitCode == 0 && result.output.isNotBlank()) {
            result.output.trim()
        } else {
            null
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
