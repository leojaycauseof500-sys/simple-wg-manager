package com.leojay.simplewgad.component

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
    }

    @PostConstruct
    fun checkOnStartup() {
        logger.info("Checking WireGuard status on application startup...")
        val status = wireGuardService.checkWireGuardStatus()
        isWireGuardRunning = status.totalStatus == com.leojay.simplewgad.model.ServiceStatus.RUNNING
        startupStatusChecked = true

        logger.info("WireGuard status: ${status.totalStatus}")
        logger.info("Process running: ${status.isProcessRunning}")
        logger.info("Interface: ${status.interfaceName ?: "None"}")

        if (!isWireGuardRunning) {
            logger.warn("WireGuard is not running. Application will show a warning page.")
        }
    }
}
