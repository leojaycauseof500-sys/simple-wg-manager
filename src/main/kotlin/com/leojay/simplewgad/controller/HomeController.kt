package com.leojay.simplewgad.controller

import com.leojay.simplewgad.component.StartupWireGuardCheck
import com.leojay.simplewgad.service.StatisticsService
import com.leojay.simplewgad.service.WireGuardService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/")
class HomeController(
    private val wireGuardService: WireGuardService,
    private val startupWireGuardCheck: StartupWireGuardCheck,
    private val statisticsService: StatisticsService
) {

    @GetMapping("/")
    fun homePage(model: Model): String {
        // 检查 WireGuard 状态
        val statusResult = wireGuardService.checkWireGuardStatus()

        statusResult.fold(
            onSuccess = { status ->
                // 如果 WireGuard 没有运行，返回特殊页面
                if (status.totalStatus != com.leojay.simplewgad.model.ServiceStatus.RUNNING) {
                    model.addAttribute("status", status)
                    model.addAttribute("isStartupCheck", StartupWireGuardCheck.startupStatusChecked)
                    model.addAttribute("startupStatus", StartupWireGuardCheck.isWireGuardRunning)
                    return "wireguard-not-running"
                }

                // 正常情况返回主页
                return "index"
            },
            onFailure = { exception ->
                // 处理错误情况
                model.addAttribute("error", exception.message)
                model.addAttribute("isStartupCheck", StartupWireGuardCheck.startupStatusChecked)
                model.addAttribute("startupStatus", StartupWireGuardCheck.isWireGuardRunning)
                return "wireguard-not-running"
            }
        )
    }

    @GetMapping("/api/statistics/dashboard")
    @ResponseBody
    fun getDashboardStatistics(): ResponseEntity<Any> {
        return statisticsService.getDashboardStatistics()
            .fold(
                onSuccess = { stats ->
                    ResponseEntity.ok(stats)
                },
                onFailure = { exception ->
                    ResponseEntity.status(500).body(mapOf(
                        "error" to "获取统计数据失败",
                        "message" to exception.message
                    ))
                }
            )
    }

    @GetMapping("/api/statistics/online-clients")
    @ResponseBody
    fun getOnlineClients(): ResponseEntity<Any> {
        return statisticsService.getOnlineClients()
            .fold(
                onSuccess = { clients ->
                    ResponseEntity.ok(clients)
                },
                onFailure = { exception ->
                    ResponseEntity.status(500).body(mapOf(
                        "error" to "获取在线客户端失败",
                        "message" to exception.message
                    ))
                }
            )
    }

    @GetMapping("/api/statistics/traffic")
    @ResponseBody
    fun getTrafficStatistics(): ResponseEntity<Any> {
        return statisticsService.getTrafficStatistics()
            .fold(
                onSuccess = { traffic ->
                    ResponseEntity.ok(traffic)
                },
                onFailure = { exception ->
                    ResponseEntity.status(500).body(mapOf(
                        "error" to "获取流量统计失败",
                        "message" to exception.message
                    ))
                }
            )
    }

    @GetMapping("/api/statistics/uptime")
    @ResponseBody
    fun getUptimeStatistics(): ResponseEntity<Any> {
        return statisticsService.getUptimeStatistics()
            .fold(
                onSuccess = { uptime ->
                    ResponseEntity.ok(uptime)
                },
                onFailure = { exception ->
                    ResponseEntity.status(500).body(mapOf(
                        "error" to "获取运行时间统计失败",
                        "message" to exception.message
                    ))
                }
            )
    }

}
