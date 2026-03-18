package com.leojay.simplewgad.service

import java.time.Duration

/**
 * 统计服务接口
 */
interface StatisticsService {

    /**
     * 获取仪表板统计数据
     */
    fun getDashboardStatistics(): Result<DashboardStatistics>

    /**
     * 获取在线客户端列表
     */
    fun getOnlineClients(): Result<List<OnlineClient>>

    /**
     * 获取流量统计
     */
    fun getTrafficStatistics(): Result<TrafficStatistics>

    /**
     * 获取运行时间统计
     */
    fun getUptimeStatistics(): Result<UptimeStatistics>
}

/**
 * 仪表板统计数据
 */
data class DashboardStatistics(
    val onlineClients: Int,
    val todayTraffic: TrafficData,
    val uptime: Long, // 秒数
    val serverStatus: ServerStatus,
    val trafficChange: Double, // 百分比变化
    val clientChange: Int // 客户端数量变化
)

/**
 * 在线客户端信息
 */
data class OnlineClient(
    val name: String,
    val publicKey: String,
    val endpoint: String,
    val latestHandshake: Long, // Unix时间戳
    val transferRx: Long, // 接收字节数
    val transferTx: Long, // 发送字节数
    val allowedIps: List<String>,
    val isOnline: Boolean
)

/**
 * 流量统计数据
 */
data class TrafficStatistics(
    val today: TrafficData,
    val yesterday: TrafficData,
    val thisWeek: TrafficData,
    val thisMonth: TrafficData,
    val total: TrafficData
)

/**
 * 流量数据
 */
data class TrafficData(
    val rx: Long, // 接收字节数
    val tx: Long, // 发送字节数
    val total: Long // 总字节数
)

/**
 * 运行时间统计
 */
data class UptimeStatistics(
    val systemUptime: Duration,
    val wireguardUptime: Duration,
    val serviceUptime: Duration
)

/**
 * 服务器状态
 */
enum class ServerStatus {
    RUNNING,
    STOPPED,
    ERROR,
    STARTING
}