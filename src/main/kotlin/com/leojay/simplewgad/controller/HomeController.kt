package com.leojay.simplewgad.controller

import com.leojay.simplewgad.component.StartupWireGuardCheck
import com.leojay.simplewgad.service.WireGuardService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController(
    private val wireGuardService: WireGuardService
) {
    
    @GetMapping("/")
    fun homePage(model: Model): String {
        // 检查 WireGuard 状态
        val status = wireGuardService.checkWireGuardStatus()
        
        // 如果 WireGuard 没有运行，返回特殊页面
        if (status.totalStatus != com.leojay.simplewgad.model.ServiceStatus.RUNNING) {
            model.addAttribute("status", status)
            model.addAttribute("isStartupCheck", StartupWireGuardCheck.startupStatusChecked)
            model.addAttribute("startupStatus", StartupWireGuardCheck.isWireGuardRunning)
            return "wireguard-not-running"
        }
        
        // 正常情况返回主页
        return "index"
    }
}
