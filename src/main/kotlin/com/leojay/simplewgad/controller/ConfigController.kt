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
        val serverConfigResult = wireGuardService.getServerConfig()

        serverConfigResult.fold(
            onSuccess = { serverConfig ->
                model.addAttribute("serverConfig", serverConfig)
                model.addAttribute("hasConfig", true)
            },
            onFailure = { exception ->
                model.addAttribute("hasConfig", false)
                model.addAttribute("errorMessage", exception.message)
            }
        )

        return "config"
    }
}
