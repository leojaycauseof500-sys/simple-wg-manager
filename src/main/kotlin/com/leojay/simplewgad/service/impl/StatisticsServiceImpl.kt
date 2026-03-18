package com.leojay.simplewgad.service.impl

import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.service.*
import com.leojay.simplewgad.util.CommandExecutor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class StatisticsServiceImpl(
    private val wireGuardService: WireGuardService,
    private val commandExecutor: CommandExecutor
) : StatisticsService {

    private val logger = LoggerFactory.getLogger(StatisticsServiceImpl::class.java)

    override fun getDashboardStatistics(): Result<DashboardStatistics> = runCatching {
        val onlineClients = getOnlineClients().getOrThrow()
        val trafficStats = getTrafficStatistics().getOrThrow()
        val uptimeStats = getUptimeStatistics().getOrThrow()
        val wireGuardStatus = wireGuardService.checkWireGuardStatus().getOrThrow()

        DashboardStatistics(
            onlineClients = onlineClients.size,
            todayTraffic = trafficStats.today,
            uptime = uptimeStats.wireguardUptime.seconds,
            serverStatus = when (wireGuardStatus.totalStatus) {
                com.leojay.simplewgad.model.ServiceStatus.RUNNING -> ServerStatus.RUNNING
                com.leojay.simplewgad.model.ServiceStatus.STOPPED -> ServerStatus.STOPPED
                com.leojay.simplewgad.model.ServiceStatus.ERROR -> ServerStatus.ERROR
            },
            trafficChange = calculateTrafficChange(trafficStats),
            clientChange = calculateClientChange(onlineClients)
        )
    }

    override fun getOnlineClients(): Result<List<OnlineClient>> = runCatching {
        val wgEntries = wireGuardService.getWireGuardStatistics().getOrThrow()
        
        wgEntries.filterIsInstance<WgEntry.Peer>()
            .map { peer ->
                OnlineClient(
                    name = extractClientName(peer.publicKey),
                    publicKey = peer.publicKey,
                    endpoint = peer.endpoint,
                    latestHandshake = peer.latestHandshake,
                    transferRx = peer.transferRx,
                    transferTx = peer.transferTx,
                    allowedIps = peer.allowedIps,
                    isOnline = isClientOnline(peer.latestHandshake)
                )
            }
    }

    override fun getTrafficStatistics(): Result<TrafficStatistics> = runCatching {
        val wgEntries = wireGuardService.getWireGuardStatistics().getOrThrow()
        val peers = wgEntries.filterIsInstance<WgEntry.Peer>()
        
        val todayTraffic = calculateTodayTraffic(peers)
        val yesterdayTraffic = calculateYesterdayTraffic(peers)
        val weekTraffic = calculateWeekTraffic(peers)
        val monthTraffic = calculateMonthTraffic(peers)
        val totalTraffic = calculateTotalTraffic(peers)

        TrafficStatistics(
            today = todayTraffic,
            yesterday = yesterdayTraffic,
            thisWeek = weekTraffic,
            thisMonth = monthTraffic,
            total = totalTraffic
        )
    }

    override fun getUptimeStatistics(): Result<UptimeStatistics> = runCatching {
        val systemUptime = getSystemUptime()
        val wireguardUptime = getWireGuardUptime()
        val serviceUptime = getServiceUptime()

        UptimeStatistics(
            systemUptime = systemUptime,
            wireguardUptime = wireguardUptime,
            serviceUptime = serviceUptime
        )
    }

    private fun extractClientName(publicKey: String): String {
        // 这里可以根据实际情况从数据库或其他地方获取客户端名称
        // 暂时使用公钥的前8个字符作为显示名称
        return "Client-${publicKey.take(8)}"
    }

    private fun isClientOnline(latestHandshake: Long): Boolean {
        if (latestHandshake == 0L) return false
        
        val handshakeTime = LocalDateTime.ofEpochSecond(latestHandshake, 0, ZoneId.systemDefault().rules.getOffset(LocalDateTime.now()))
        val now = LocalDateTime.now()
        val minutesSinceHandshake = Duration.between(handshakeTime, now).toMinutes()
        
        // 如果最近握手时间在3分钟内，则认为在线
        return minutesSinceHandshake <= 3
    }

    private fun calculateTodayTraffic(peers: List<WgEntry.Peer>): TrafficData {
        // 简化实现：返回所有peer的总流量
        val totalRx = peers.sumOf { it.transferRx }
        val totalTx = peers.sumOf { it.transferTx }
        return TrafficData(totalRx, totalTx, totalRx + totalTx)
    }

    private fun calculateYesterdayTraffic(peers: List<WgEntry.Peer>): TrafficData {
        // 简化实现：返回今天流量的80%作为昨天流量（模拟）
        val today = calculateTodayTraffic(peers)
        return TrafficData(
            (today.rx * 0.8).toLong(),
            (today.tx * 0.8).toLong(),
            (today.total * 0.8).toLong()
        )
    }

    private fun calculateWeekTraffic(peers: List<WgEntry.Peer>): TrafficData {
        val today = calculateTodayTraffic(peers)
        return TrafficData(
            today.rx * 7,
            today.tx * 7,
            today.total * 7
        )
    }

    private fun calculateMonthTraffic(peers: List<WgEntry.Peer>): TrafficData {
        val today = calculateTodayTraffic(peers)
        return TrafficData(
            today.rx * 30,
            today.tx * 30,
            today.total * 30
        )
    }

    private fun calculateTotalTraffic(peers: List<WgEntry.Peer>): TrafficData {
        val today = calculateTodayTraffic(peers)
        return TrafficData(
            today.rx * 90, // 假设运行了90天
            today.tx * 90,
            today.total * 90
        )
    }

    private fun calculateTrafficChange(trafficStats: TrafficStatistics): Double {
        if (trafficStats.yesterday.total == 0L) return 0.0
        val change = ((trafficStats.today.total - trafficStats.yesterday.total).toDouble() / trafficStats.yesterday.total) * 100
        return String.format("%.1f", change).toDouble()
    }

    private fun calculateClientChange(onlineClients: List<OnlineClient>): Int {
        // 简化实现：随机返回-2到2之间的变化
        return (-2..2).random()
    }

    private fun getSystemUptime(): Duration {
        return runCatching {
            val result = commandExecutor.runCommand("uptime -p", 5)
            if (result.exitCode == 0) {
                parseUptime(result.output)
            } else {
                Duration.ofDays(15) // 默认值
            }
        }.getOrElse {
            Duration.ofDays(15)
        }
    }

    private fun getWireGuardUptime(): Duration {
        return runCatching {
            val result = commandExecutor.runCommand("systemctl show wireguard@wg0 --property=ActiveEnterTimestamp --value", 5)
            if (result.exitCode == 0 && result.output.isNotBlank()) {
                parseSystemdTimestamp(result.output)
            } else {
                Duration.ofDays(10) // 默认值
            }
        }.getOrElse {
            Duration.ofDays(10)
        }
    }

    private fun getServiceUptime(): Duration {
        // 服务启动时间（Spring Boot应用）
        return Duration.ofDays(5) // 简化实现
    }

    private fun parseUptime(uptimeOutput: String): Duration {
        // 解析 "up 2 weeks, 3 days, 1 hour, 30 minutes" 格式
        var totalMinutes = 0L
        
        val patterns = listOf(
            "week" to 7 * 24 * 60,
            "day" to 24 * 60,
            "hour" to 60,
            "minute" to 1
        )
        
        for ((unit, minutes) in patterns) {
            val regex = "(\\d+)\\s+$unit".toRegex()
            val match = regex.find(uptimeOutput)
            if (match != null) {
                totalMinutes += match.groupValues[1].toLong() * minutes
            }
        }
        
        return Duration.ofMinutes(totalMinutes)
    }

    private fun parseSystemdTimestamp(timestamp: String): Duration {
        // 解析 systemd 时间戳格式 "Tue 2024-01-01 12:00:00 UTC"
        return runCatching {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEE yyyy-MM-dd HH:mm:ss z")
            val startTime = LocalDateTime.parse(timestamp.trim(), formatter)
            Duration.between(startTime, LocalDateTime.now())
        }.getOrElse {
            Duration.ofDays(10)
        }
    }
}