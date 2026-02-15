package com.leojay.simplewgad.model

data class WireGuardStatus (
    val isProcessRunning: Boolean,
    val interfaceName: String?,
//    val configPath: String?,
//    val configFileExists: Boolean,
//    val isInterfaceUp: Boolean,
//    val publicKey: String?,
    val totalStatus: ServiceStatus // enum: RUNNING, STOPPED, ERROR
)

enum class ServiceStatus {
    RUNNING,
    STOPPED,
    ERROR
}
