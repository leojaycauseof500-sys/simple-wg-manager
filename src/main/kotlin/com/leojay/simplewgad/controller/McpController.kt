package com.leojay.simplewgad.controller

import com.leojay.simplewgad.model.JsonRpcError
import com.leojay.simplewgad.model.JsonRpcRequest
import com.leojay.simplewgad.model.JsonRpcResponse
import com.leojay.simplewgad.service.StatisticsService
import com.leojay.simplewgad.service.WireGuardService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/mcp")
class McpController(
    private val wireGuardService: WireGuardService,
    private val statisticsService: StatisticsService
) {
    private val sseEmitters = ConcurrentHashMap<String, SseEmitter>()

    @GetMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun handleSse() : SseEmitter {

        val sessionId = UUID.randomUUID().toString()
        val emitter = SseEmitter(60000).also {
            sseEmitters[sessionId] = it
            it.onCompletion { sseEmitters.remove(sessionId) }
            it.onTimeout { sseEmitters.remove(sessionId) }
        }

        emitter.send(SseEmitter.event().name("endpoint").data("/mcp?sessionId=$sessionId"))
        return emitter
    }

    @PostMapping
    fun handleMessage(@RequestBody request: JsonRpcRequest) : ResponseEntity<JsonRpcResponse>{
        if(request.id == null){
            return ResponseEntity.accepted().build()
        }


        val response = when(request.method){
            "initialize" -> handleInitialize(request)
            "tools/list" -> handleToolsList(request)
            "tools/call" -> handleToolsCall(request)
            else -> JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32601,
                    message = "The method does not exist / is not available",
                    data = Instant.now()
                )
            )
        }

        return ResponseEntity(response, HttpStatus.OK)
    }

    private fun handleInitialize(request: JsonRpcRequest): JsonRpcResponse {
        return JsonRpcResponse(
            id = request.id,
            result = mapOf(
                "protocolVersion" to "2025-11-25",
                "serverInfo" to mapOf(
                    "name" to "wireguard-mcp-server",
                    "version" to "1.0.0"
                ),
                "capabilities" to mapOf(
                    "tools" to mapOf(
                        "listChanged" to true
                    )
                )
            )
        )
    }

    private fun handleToolsList(request: JsonRpcRequest): JsonRpcResponse {
        val tools = listOf(
            mapOf(
                "name" to "get_wireguard_status",
                "description" to "获取WireGuard服务状态（运行/停止/错误）",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_wireguard_details",
                "description" to "获取详细的WireGuard状态信息",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_wireguard_statistics",
                "description" to "获取WireGuard接口统计信息",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_interface_config",
                "description" to "获取接口配置信息",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_server_config",
                "description" to "获取服务器完整配置信息",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "add_client",
                "description" to "添加新客户端",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "clientName" to mapOf(
                            "type" to "string",
                            "description" to "客户端名称"
                        ),
                        "allowedIps" to mapOf(
                            "type" to "string",
                            "description" to "允许的IP地址范围，如：10.0.0.2/32"
                        )
                    ),
                    "required" to listOf("clientName", "allowedIps")
                )
            ),
            mapOf(
                "name" to "delete_client",
                "description" to "删除客户端",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "clientUuid" to mapOf(
                            "type" to "string",
                            "description" to "客户端UUID"
                        )
                    ),
                    "required" to listOf("clientUuid")
                )
            ),
            mapOf(
                "name" to "get_client_config",
                "description" to "获取客户端配置",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "clientUuid" to mapOf(
                            "type" to "string",
                            "description" to "客户端UUID"
                        )
                    ),
                    "required" to listOf("clientUuid")
                )
            ),
            mapOf(
                "name" to "get_online_clients",
                "description" to "获取在线客户端列表",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_config_file_content",
                "description" to "获取配置文件内容",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "interfaceName" to mapOf(
                            "type" to "string",
                            "description" to "接口名称，如：wg0"
                        )
                    ),
                    "required" to listOf("interfaceName")
                )
            ),
            mapOf(
                "name" to "restart_wireguard_service",
                "description" to "重启WireGuard服务",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_system_info",
                "description" to "获取系统信息（内核版本、WireGuard版本等）",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_dashboard_statistics",
                "description" to "获取仪表板统计数据",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_traffic_statistics",
                "description" to "获取流量统计信息",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            ),
            mapOf(
                "name" to "get_uptime_statistics",
                "description" to "获取运行时间统计",
                "inputSchema" to mapOf(
                    "type" to "object",
                    "properties" to emptyMap<String, Any>()
                )
            )
        )

        return JsonRpcResponse(
            id = request.id,
            result = mapOf("tools" to tools)
        )
    }

    private fun handleToolsCall(request: JsonRpcRequest): JsonRpcResponse {
        val toolName = request.params?.get("name") as? String
        val arguments = request.params?.get("arguments") as? Map<String, Any>

        if (toolName == null) {
            return JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: tool name is required",
                    data = Instant.now()
                )
            )
        }

        return try {
            val result = when (toolName) {
                "get_wireguard_status" -> handleGetWireGuardStatus()
                "get_wireguard_details" -> handleGetWireGuardDetails()
                "get_wireguard_statistics" -> handleGetWireGuardStatistics()
                "get_interface_config" -> handleGetInterfaceConfig()
                "get_server_config" -> handleGetServerConfig()
                "add_client" -> handleAddClient(arguments)
                "delete_client" -> handleDeleteClient(arguments)
                "get_client_config" -> handleGetClientConfig(arguments)
                "get_online_clients" -> handleGetOnlineClients()
                "get_config_file_content" -> handleGetConfigFileContent(arguments)
                "restart_wireguard_service" -> handleRestartWireGuardService()
                "get_system_info" -> handleGetSystemInfo()
                "get_dashboard_statistics" -> handleGetDashboardStatistics()
                "get_traffic_statistics" -> handleGetTrafficStatistics()
                "get_uptime_statistics" -> handleGetUptimeStatistics()
                else -> throw IllegalArgumentException("Unknown tool: $toolName")
            }

            JsonRpcResponse(
                id = request.id,
                result = mapOf(
                    "content" to listOf(
                        mapOf(
                            "type" to "text",
                            "text" to result.toString()
                        )
                    )
                )
            )
        } catch (e: Exception) {
            JsonRpcResponse(
                id = request.id,
                error = JsonRpcError(
                    code = -32000,
                    message = "Tool execution failed: ${e.message}",
                    data = Instant.now()
                )
            )
        }
    }

    private fun handleGetWireGuardStatus(): Any {
        return wireGuardService.checkWireGuardStatus().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetWireGuardDetails(): Any {
        return wireGuardService.getWireGuardDetails().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetWireGuardStatistics(): Any {
        return wireGuardService.getWireGuardStatistics().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetInterfaceConfig(): Any {
        return wireGuardService.getInterfaceConfig().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetServerConfig(): Any {
        return wireGuardService.getServerConfig().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleAddClient(arguments: Map<String, Any>?): Any {
        val clientName = arguments?.get("clientName") as? String
        val allowedIps = arguments?.get("allowedIps") as? String

        if (clientName == null || allowedIps == null) {
            throw IllegalArgumentException("clientName and allowedIps are required")
        }

        return wireGuardService.addClient(clientName, allowedIps).fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleDeleteClient(arguments: Map<String, Any>?): Any {
        val clientUuid = arguments?.get("clientUuid") as? String
            ?: throw IllegalArgumentException("clientUuid is required")

        return wireGuardService.deleteClient(clientUuid).fold(
            onSuccess = { "Client deleted successfully" },
            onFailure = { throw it }
        )
    }

    private fun handleGetClientConfig(arguments: Map<String, Any>?): Any {
        val clientUuid = arguments?.get("clientUuid") as? String
            ?: throw IllegalArgumentException("clientUuid is required")

        return wireGuardService.getClientConfig(clientUuid).fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetOnlineClients(): Any {
        return statisticsService.getOnlineClients().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetConfigFileContent(arguments: Map<String, Any>?): Any {
        val interfaceName = arguments?.get("interfaceName") as? String
            ?: throw IllegalArgumentException("interfaceName is required")

        return wireGuardService.getConfigFileContent(interfaceName).fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleRestartWireGuardService(): Any {
        return wireGuardService.restartWireGuardService().fold(
            onSuccess = { "WireGuard service restarted successfully" },
            onFailure = { throw it }
        )
    }

    private fun handleGetSystemInfo(): Any {
        return wireGuardService.getSystemInfo().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetDashboardStatistics(): Any {
        return statisticsService.getDashboardStatistics().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetTrafficStatistics(): Any {
        return statisticsService.getTrafficStatistics().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }

    private fun handleGetUptimeStatistics(): Any {
        return statisticsService.getUptimeStatistics().fold(
            onSuccess = { it },
            onFailure = { throw it }
        )
    }
}