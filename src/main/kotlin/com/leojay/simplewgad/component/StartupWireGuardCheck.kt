package com.leojay.simplewgad.component

import com.leojay.simplewgad.model.ServiceStatus
import com.leojay.simplewgad.model.WireGuardStatus
import com.leojay.simplewgad.service.WireGuardService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class StartupWireGuardCheck(
    private val wireGuardService: WireGuardService
) {
    private val logger = LoggerFactory.getLogger(StartupWireGuardCheck::class.java)

    companion object {
        var isWireGuardRunning: Boolean = false
        var startupStatusChecked: Boolean = false
        var startupErrorMessage: String? = null
    }

    @PostConstruct
    fun checkOnStartup() {
        logger.info("Checking WireGuard status on application startup...")

        val statusResult = wireGuardService.checkWireGuardStatus()

        statusResult.fold(
            onSuccess = { status ->
                isWireGuardRunning = status.totalStatus == ServiceStatus.RUNNING
                startupStatusChecked = true
                startupErrorMessage = null

                logger.info("WireGuard status: ${status.totalStatus}")
                logger.info("Process running: ${status.isProcessRunning}")
                logger.info("Interface: ${status.interfaceName ?: "None"}")

                if (!isWireGuardRunning) {
                    logger.warn("WireGuard is not running. Application will show a warning page.")
                }
            },
            onFailure = { exception ->
                startupStatusChecked = true
                isWireGuardRunning = false
                startupErrorMessage = exception.message ?: "Unknown error"

                logger.error("Failed to check WireGuard status on startup: ${exception.message}", exception)
                logger.warn("WireGuard status check failed. Application will show a warning page.")
            }
        )
    }

    /**
     * 获取启动检查的详细信息
     */
    fun getStartupCheckInfo(): StartupCheckInfo {
        return StartupCheckInfo(
            isChecked = startupStatusChecked,
            isRunning = isWireGuardRunning,
            errorMessage = startupErrorMessage
        )
    }

    /**
     * 重新检查 WireGuard 状态
     */
    fun recheckStatus(): RecheckResult {
        logger.info("Rechecking WireGuard status...")

        val statusResult = wireGuardService.checkWireGuardStatus()

        return statusResult.fold(
            onSuccess = { status ->
                val wasRunning = isWireGuardRunning
                isWireGuardRunning = status.totalStatus == ServiceStatus.RUNNING
                startupErrorMessage = null

                logger.info("Recheck completed. Status: ${status.totalStatus}, Running: $isWireGuardRunning")

                RecheckResult(
                    success = true,
                    isRunning = isWireGuardRunning,
                    status = status,
                    statusChanged = wasRunning != isWireGuardRunning
                )
            },
            onFailure = { exception ->
                startupErrorMessage = exception.message ?: "Unknown error"
                logger.error("Recheck failed: ${exception.message}", exception)

                RecheckResult(
                    success = false,
                    isRunning = false,
                    errorMessage = exception.message,
                    statusChanged = false
                )
            }
        )
    }
}

/**
 * 启动检查信息
 */
data class StartupCheckInfo(
    val isChecked: Boolean,
    val isRunning: Boolean,
    val errorMessage: String?
)

/**
 * 重新检查结果
 */
data class RecheckResult(
    val success: Boolean,
    val isRunning: Boolean,
    val status: WireGuardStatus? = null,
    val errorMessage: String? = null,
    val statusChanged: Boolean
)
