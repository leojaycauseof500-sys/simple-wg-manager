package com.leojay.simplewgad.component

import com.leojay.simplewgad.model.ServiceStatus
import com.leojay.simplewgad.repository.PeerMetaDataRepository
import com.leojay.simplewgad.service.WireGuardService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class StartupWireGuardCheck(
    private val wireGuardService: WireGuardService,
    private val peerMetaDataRepository: PeerMetaDataRepository
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
    //todo 二次检查先注释掉，后续需要再重写
//    fun recheckStatus(): RecheckResult {
//        logger.info("Rechecking WireGuard status...")
//
//        val statusResult = wireGuardService.checkWireGuardStatus()
//
//    }
}

/**
 * 启动检查信息
 */
data class StartupCheckInfo(
    val isChecked: Boolean,
    val isRunning: Boolean,
    val errorMessage: String?
)


