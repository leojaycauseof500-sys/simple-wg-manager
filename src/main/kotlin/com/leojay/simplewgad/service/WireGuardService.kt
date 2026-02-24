package com.leojay.simplewgad.service

import com.leojay.simplewgad.model.InterfaceConfig
import com.leojay.simplewgad.model.WireGuardStatus
import com.leojay.simplewgad.model.WgEntry

interface WireGuardService {

    /**
     * 检查 WireGuard 状态
     */
    fun checkWireGuardStatus(): Result<WireGuardStatus>

    /**
     * 获取 WireGuard 详细状态信息
     */
    fun getWireGuardDetails(): Result<String>

    /**
     * 获取 WireGuard 接口统计信息
     */
    fun getWireGuardStatistics(): Result<List<WgEntry>>

    /**
     * 获取接口配置
     */
    fun getInterfaceConfig(): Result<InterfaceConfig>

    /**
     * 获取服务器配置信息
     */
    fun getServerConfig(): Result<ServerConfig>

    /**
     * 添加新客户端
     */
    fun addClient(clientName: String, allowedIps: String): Result<ClientConfigResult>

    /**
     * 重启 WireGuard 服务
     */
    fun restartWireGuardService(): Result<Unit>

    /**
     * 获取配置文件内容
     */
    fun getConfigFileContent(interfaceName: String): Result<String>

    /**
     * 获取系统信息
     */
    fun getSystemInfo(): Result<SystemInfo>
}

/**
 * 服务器配置数据类
 */
data class ServerConfig(
    val interfaceConfig: InterfaceConfig,
    val configFileContent: String?,
    val systemInfo: SystemInfo
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
    val clientName: String,
    val clientConfig: String,
    val publicKey: String,
    val privateKey: String,
    val qrCode: String? = null
)
