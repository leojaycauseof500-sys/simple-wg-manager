package com.leojay.simplewgad.controller

import com.leojay.simplewgad.model.ClientMetaData
import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.repository.PeerMetaDataRepository
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
    private val wireGuardService: WireGuardService,
    private val peerMetaDataRepository: PeerMetaDataRepository
) {

    @GetMapping("/clients")
    fun clientsPage(model: Model): String {
        // 获取客户端元数据
        val peerMetaData = peerMetaDataRepository.getMaskedData()
        val clientsMeta = peerMetaData.clients
        
        // 获取实时 WireGuard 统计信息
        val wgStatsResult = wireGuardService.getWireGuardStatistics()
        
        // 构建一个从公钥到实时统计信息的映射
        val realtimeStatsByPublicKey = mutableMapOf<String, WgEntry.Peer>()
        wgStatsResult.fold(
            onSuccess = { wgEntries ->
                wgEntries.forEach { entry ->
                    if (entry is WgEntry.Peer) {
                        realtimeStatsByPublicKey[entry.publicKey] = entry
                    }
                }
            },
            onFailure = { exception ->
                // 如果获取实时统计失败，可以记录日志，但继续显示客户端元数据
                model.addAttribute("statsError", exception.message)
            }
        )
        
        // 构建客户端显示数据列表
        val clientDisplayList = clientsMeta.map { (uuid, clientMeta) ->
            val realtimeStats = realtimeStatsByPublicKey[clientMeta.publicKey]
            ClientDisplayData(
                uuid = uuid,
                clientMeta = clientMeta,
                realtimeStats = realtimeStats
            )
        }
        
        model.addAttribute("clients", clientDisplayList)
        model.addAttribute("hasClients", clientsMeta.isNotEmpty())
        
        // 保留原有的服务器配置获取（用于其他部分）
        val serverConfigResult = wireGuardService.getServerConfig()
        serverConfigResult.fold(
            onSuccess = { serverConfig ->
                model.addAttribute("serverConfig", serverConfig)
                model.addAttribute("hasConfig", true)
            },
            onFailure = { exception ->
                model.addAttribute("hasConfig", false)
                model.addAttribute("configErrorMessage", exception.message)
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
        
        // 重新加载页面数据
        return clientsPage(model)
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

/**
 * 用于前端展示的客户端数据，合并了元数据和实时统计信息
 */
data class ClientDisplayData(
    val uuid: String,
    val clientMeta: ClientMetaData,
    val realtimeStats: WgEntry.Peer?
)
