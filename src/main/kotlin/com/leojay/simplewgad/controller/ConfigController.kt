package com.leojay.simplewgad.controller

import com.leojay.simplewgad.service.WireGuardService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class ConfigController(
    private val wireGuardService: WireGuardService
) {
    
    @GetMapping("/config")
    fun configPage(model: Model): String {
        // 获取服务器配置信息
        val serverConfig = wireGuardService.getServerConfig()
        
        model.addAttribute("serverConfig", serverConfig)
        model.addAttribute("hasConfig", serverConfig.success)
        
        // 如果配置获取失败，添加错误信息
        if (!serverConfig.success) {
            model.addAttribute("errorMessage", serverConfig.errorMessage ?: "无法获取服务器配置")
        }
        
        return "config"
    }
}
