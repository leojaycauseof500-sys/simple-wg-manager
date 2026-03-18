package com.leojay.simplewgad.controller

import com.leojay.simplewgad.model.ClientMetaData
import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.repository.PeerMetaDataRepository
import com.leojay.simplewgad.service.WireGuardService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.PathVariable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Controller
class ClientController(
    private val wireGuardService: WireGuardService,
    private val peerMetaDataRepository: PeerMetaDataRepository
) {

    @GetMapping("/clients")
    fun clientsPage(
        @RequestParam(value = "success", required = false) success: Boolean?,
        @RequestParam(value = "clientName", required = false) clientName: String?,
        @RequestParam(value = "config", required = false) config: String?,
        @RequestParam(value = "publicKey", required = false) publicKey: String?,
        @RequestParam(value = "errorMessage", required = false) errorMessage: String?,
        model: Model
    ): String {
        // 处理重定向参数
        success?.let { model.addAttribute("success", it) }
        clientName?.let { model.addAttribute("clientName", it) }
        config?.let { model.addAttribute("config", it) }
        publicKey?.let { model.addAttribute("publicKey", it) }
        errorMessage?.let { model.addAttribute("errorMessage", it) }

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

        return result.fold(
            onSuccess = { clientConfig ->
                // 使用重定向参数传递成功信息
                "redirect:/clients?success=true&clientName=${URLEncoder.encode(clientConfig.clientName, StandardCharsets.UTF_8.toString())}&config=${URLEncoder.encode(clientConfig.clientConfig, StandardCharsets.UTF_8.toString())}&publicKey=${URLEncoder.encode(clientConfig.publicKey, StandardCharsets.UTF_8.toString())}"
            },
            onFailure = { exception ->
                // 使用重定向参数传递错误信息
                "redirect:/clients?success=false&errorMessage=${URLEncoder.encode(exception.message ?: "未知错误", StandardCharsets.UTF_8.toString())}"
            }
        )
    }

    @PostMapping("/clients/delete")
    @ResponseBody
    fun deleteClient(
        @RequestParam("clientUuid") clientUuid: String
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val result = wireGuardService.deleteClient(clientUuid)
            result.fold(
                onSuccess = {
                    ResponseEntity.ok(mapOf(
                        "success" to true,
                        "message" to "客户端删除成功"
                    ))
                },
                onFailure = { exception ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(mapOf(
                            "success" to false,
                            "message" to "删除失败: ${exception.message}"
                        ))
                }
            )
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf(
                    "success" to false,
                    "message" to "删除失败: ${e.message}"
                ))
        }
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

    @GetMapping("/clients/download/{clientUuid}")
    fun downloadClientConfigByUuid(
        @PathVariable clientUuid: String
    ): ResponseEntity<ByteArray> {
        return wireGuardService.getClientConfig(clientUuid).fold(
            onSuccess = { clientConfig ->
                val encodedClientName = URLEncoder.encode(clientConfig.clientName, StandardCharsets.UTF_8.toString())
                val filename = "wireguard-$encodedClientName.conf"

                val headers = HttpHeaders().apply {
                    add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                    add(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                }

                ResponseEntity.ok()
                    .headers(headers)
                    .body(clientConfig.clientConfig.toByteArray(StandardCharsets.UTF_8))
            },
            onFailure = { exception ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("客户端配置获取失败: ${exception.message}".toByteArray(StandardCharsets.UTF_8))
            }
        )
    }
}

/**
 * 用于前端展示的客户端数据，合并了元数据和实时统计信息
 */
data class ClientDisplayData(
    val uuid: String,
    val clientMeta: ClientMetaData,
    val realtimeStats: WgEntry.Peer?
) {
    fun formatHandshakeTime(): String {
        return if (realtimeStats?.latestHandshake != null && realtimeStats.latestHandshake > 0) {
            try {
                val instant = java.time.Instant.ofEpochSecond(realtimeStats.latestHandshake)
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(java.time.ZoneId.systemDefault())
                formatter.format(instant)
            } catch (e: Exception) {
                "时间格式错误"
            }
        } else {
            "从未"
        }
    }
}
