package com.leojay.simplewgad.controller

import com.leojay.simplewgad.service.WireGuardService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Controller
class ClientController(
    private val wireGuardService: WireGuardService
) {

    @GetMapping("/clients")
    fun clientsPage(model: Model): String {
        // 获取当前客户端列表
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

        return "clients"
    }

    @PostMapping("/clients/add")
    fun addClient(
        @RequestParam("clientName") clientName: String,
        @RequestParam("allowedIps", defaultValue = "10.0.0.2/32") allowedIps: String,
        model: Model
    ): String {
        val result = wireGuardService.addClient(clientName, allowedIps)

        result.fold(
            onSuccess = { clientConfig ->
                model.addAttribute("success", true)
                model.addAttribute("clientName", clientConfig.clientName)
                model.addAttribute("clientConfig", clientConfig.clientConfig)
                model.addAttribute("publicKey", clientConfig.publicKey)
                model.addAttribute("privateKey", clientConfig.privateKey)
            },
            onFailure = { exception ->
                model.addAttribute("success", false)
                model.addAttribute("errorMessage", exception.message)
            }
        )

        return "clients"
    }

    @GetMapping("/clients/download")
    fun downloadClientConfig(
        @RequestParam("clientName") clientName: String,
        @RequestParam("config") config: String
    ): ResponseEntity<ByteArray> {
        val encodedClientName = URLEncoder.encode(clientName, StandardCharsets.UTF_8.toString())
        val filename = "wireguard-$encodedClientName.conf"

        val headers = HttpHeaders().apply {
            add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            add(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
        }

        return ResponseEntity.ok()
            .headers(headers)
            .body(config.toByteArray(StandardCharsets.UTF_8))
    }
}
