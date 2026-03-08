package com.leojay.simplewgad.controller

import com.leojay.simplewgad.service.WireGuardService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class ConfigController(
    private val wireGuardService: WireGuardService,
    @Value("\${wg-manager.peer-meta-data.path}") private val dataPath: String
) {

    @GetMapping("/config")
    fun configPage(model: Model): String {
        // 获取服务器配置信息
        val serverConfigResult = wireGuardService.getServerConfig()

        serverConfigResult.fold(
            onSuccess = { serverConfig ->
                model.addAttribute("serverConfig", serverConfig)
                model.addAttribute("configPath", "$dataPath${serverConfig.interfaceName}.conf")
                model.addAttribute("hasConfig", true)
                model.addAttribute("peerCount", serverConfig.interfaceConfig.peers.size)
            },
            onFailure = { exception ->
                model.addAttribute("hasConfig", false)
                model.addAttribute("errorMessage", exception.message)
            }
        )

        return "config"
    }
}
