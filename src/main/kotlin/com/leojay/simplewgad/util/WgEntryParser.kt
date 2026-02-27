package com.leojay.simplewgad.util

import com.leojay.simplewgad.model.InterfaceConfig
import com.leojay.simplewgad.model.PeerConfig
import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.util.WgEntryParseState.InInterface.currentInterface
import com.leojay.simplewgad.util.WgEntryParseState.InPeers.currentPeer
import com.leojay.simplewgad.util.constant.Symbols
import com.tinder.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.collections.emptyList

@Component
class WgEntryParser {
    private val logger = LoggerFactory.getLogger(WgEntryParser::class.java)

    // 用于收集解析过程中的数据
    private val parsedPeers = mutableListOf<PeerConfig>()

    private var parserStateMachine = createStateMachine()

    fun parseWgDump(output: List<String>): Result<List<WgEntry>> = runCatching {
        // 前置判断1: 检查输入是否为空
        require(output.isNotEmpty()) { "输出内容为空" }

        // 前置判断2: 检查是否有有效数据行
        val nonEmptyLines = output.filter { it.isNotBlank() }
        require(nonEmptyLines.isNotEmpty()) { "没有有效的输出行" }

        val entries = mutableListOf<WgEntry>()
        var interfaceCount = 0
        var peerCount = 0

        nonEmptyLines.forEachIndexed { index, line ->
            val parts = line.split('\t')

            when (parts.size) {
                5 -> {
                    // 接口行应该有5个字段
                    try {
                        val interfaceEntry = WgEntry.Interface(
                            name = parts[0].takeIf { it.isNotBlank() } ?: "unknown",
                            publicKey = parts[2].takeIf { it.isNotBlank() } ?: "",
                            listenPort = parts[3].toIntOrNull() ?: 0
                        )
                        entries.add(interfaceEntry)
                        interfaceCount++
                    } catch (e: Exception) {
                        logger.warn("解析接口行失败 (行 ${index + 1}): $line, 错误: ${e.message}")
                    }
                }

                9 -> {
                    // Peer行应该有9个字段
                    try {
                        val peerEntry = WgEntry.Peer(
                            interfaceName = parts[0].takeIf { it.isNotBlank() } ?: "unknown",
                            publicKey = parts[1].takeIf { it.isNotBlank() } ?: "",
                            endpoint = parts[3].takeIf { it != "(none)" && it.isNotBlank() } ?: "",
                            allowedIps = parts[4].split(',').filter { it.isNotBlank() },
                            latestHandshake = parts[5].toLongOrNull() ?: 0L,
                            transferRx = parts[6].toLongOrNull() ?: 0L,
                            transferTx = parts[7].toLongOrNull() ?: 0L
                        )
                        entries.add(peerEntry)
                        peerCount++
                    } catch (e: Exception) {
                        logger.warn("解析Peer行失败 (行 ${index + 1}): $line, 错误: ${e.message}")
                    }
                }

                else -> {
                    // 记录异常行但不中断解析
                    logger.debug("跳过异常格式行 (行 ${index + 1}): $line, 字段数: ${parts.size}")
                }
            }
        }

        // 前置判断3: 检查是否解析到有效数据
        require(entries.isNotEmpty()) { "未能解析出任何有效数据" }

        // 可选: 记录解析统计信息
        logger.debug("解析完成: 接口数=$interfaceCount, Peer数=$peerCount, 总条目数=${entries.size}")

        entries
    }.onFailure { exception ->
        logger.error("解析wg dump输出失败: ${exception.message}", exception)
    }

    fun parseConfigFile(configContent: String) : Result<InterfaceConfig> = Result.runCatching {
        require(configContent.isNotBlank()) { "配置文件内容为空" }

        val lines = configContent.lines()
        require(lines.any { it.contains("[Interface]") }) {
            "配置文件缺少 [Interface] 部分"
        }

        val interfaceConfig = parseWgConfigFile(configContent)

        // 验证必要字段
        require(interfaceConfig.privateKey.isNotBlank()) {
            "Interface 缺少 privateKey"
        }
        require(interfaceConfig.address.isNotEmpty()) {
            "Interface 缺少 address"
        }

        interfaceConfig
    }

    private fun parseWgConfigFile(configContent: String): InterfaceConfig {
        resetParser()

        val lines = configContent.lines()
        lines.forEach { line ->
            parseLine(line.trim())
        }

        parserStateMachine.transition(WgEntryParseEvent.MatchEndpoint)

        return currentInterface
    }

    private fun parseLine(line: String) {
        when {
            line.startsWith("[Interface]") -> {
                parserStateMachine.transition(WgEntryParseEvent.MatchInterface)
                // 开始新的Interface
                currentInterface = InterfaceConfig("", 0, emptyList(), null)
            }

            line.startsWith("[Peer]") -> {
                parserStateMachine.transition(WgEntryParseEvent.MatchPeer)
                // 开始新的Peer
                currentPeer = PeerConfig(publicKey = "", allowedIPs = emptyList())
            }

            line.isEmpty() || line.startsWith("#") -> {
                // 跳过空行和注释
            }

            line.contains("=") -> {
                val (key, value) = line.split("=", limit = 2).map { it.trim() }
                parserStateMachine.transition(WgEntryParseEvent.MatchKV(key = key, value = value))
            }

            else -> {
                parserStateMachine.transition(WgEntryParseEvent.MatchUnknown)
            }
        }
    }

    private fun handleKeyValue(key: String, value: String, state: WgEntryParseState) {
        // 前置检查1: 确保键和值都不为空
        require(key.isNotBlank()) { "配置键不能为空" }
        require(value.isNotBlank()) { "配置值不能为空，键: $key" }

        when (state) {
            is WgEntryParseState.InInterface -> {
                currentInterface = currentInterface.let { interfaceConfig ->
                    when (key.lowercase()) {
                        "privatekey" -> {
                            require(interfaceConfig.privateKey.isBlank()) {
                                "PrivateKey已设置，不能重复设置"
                            }
                            require(value.matches(Regex("^[A-Za-z0-9+/=]{44}\$"))) {
                                "PrivateKey格式不正确，应为44个字符的base64编码"
                            }
                            interfaceConfig.copy(privateKey = value)
                        }

                        "address" -> {
                            val addresses = value.split(Symbols.COMMA)
                            addresses.forEach { addr ->
                                require(addr.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}\$")) ||
                                    addr.matches(Regex("^[a-fA-F0-9:]+/\\d{1,3}\$"))) {
                                    "地址格式不正确: $addr"
                                }
                            }
                            interfaceConfig.copy(address = addresses)
                        }

                        "listenport" -> {
                            val port = value.toIntOrNull()
                            require(port != null) { "ListenPort必须是数字: $value" }
                            require(port in 1..65535) { "ListenPort必须在1-65535范围内: $port" }
                            interfaceConfig.copy(listenPort = port)
                        }

                        "dns" -> {
                            val dnsServers = value.split(Symbols.COMMA)
                            dnsServers.forEach { dns ->
                                require(dns.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\$")) ||
                                    dns.matches(Regex("^[a-fA-F0-9:]+$"))) {
                                    "DNS服务器格式不正确: $dns"
                                }
                            }
                            interfaceConfig.copy(dns = dnsServers)
                        }

                        else -> {
                            logger.warn("未知的Interface配置项: $key = $value")
                            interfaceConfig
                        }
                    }
                }
            }

            is WgEntryParseState.InPeers -> {
                require(currentPeer != null) { "Peer配置未初始化" }

                currentPeer = currentPeer?.let { peerConfig ->
                    when (key.lowercase()) {
                        "publickey" -> {
                            require(value.matches(Regex("^[A-Za-z0-9+/=]{44}\$"))) {
                                "PublicKey格式不正确，应为44个字符的base64编码"
                            }
                            peerConfig.copy(publicKey = value)
                        }

                        "allowedips" -> {
                            val allowedIPs = value.split(Symbols.COMMA)
                            allowedIPs.forEach { ip ->
                                require(ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}\$")) ||
                                    ip.matches(Regex("^[a-fA-F0-9:]+/\\d{1,3}\$"))) {
                                    "AllowedIPs格式不正确: $ip"
                                }
                            }
                            peerConfig.copy(allowedIPs = allowedIPs)
                        }

                        "endpoint" -> {
                            require(value.matches(Regex("^[a-zA-Z0-9.-]+:\\d{1,5}\$")) ||
                                value.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}\$"))) {
                                "Endpoint格式不正确，应为 host:port 或 ip:port: $value"
                            }
                            peerConfig.copy(endpoint = value)
                        }

                        "persistentkeepalive" -> {
                            val keepalive = value.toIntOrNull()
                            require(keepalive != null) { "PersistentKeepalive必须是数字: $value" }
                            require(keepalive >= 0 && keepalive <= 65535) {
                                "PersistentKeepalive必须在0-65535范围内: $keepalive"
                            }
                            peerConfig.copy(persistentKeepalive = keepalive)
                        }

                        "presharedkey" -> {
                            require(value.matches(Regex("^[A-Za-z0-9+/=]{44}\$"))) {
                                "PresharedKey格式不正确，应为44个字符的base64编码"
                            }
                            peerConfig.copy(presharedKey = value)
                        }

                        else -> {
                            logger.warn("未知的Peer配置项: $key = $value")
                            peerConfig
                        }
                    }
                }
            }

            else -> {
                // 前置检查13: 确保在正确的状态下处理键值对
                logger.error("在错误的状态下处理键值对: state=${parserStateMachine.state}, key=$key, value=$value")
                throw IllegalStateException("不能在 ${parserStateMachine.state} 状态下处理配置项")
            }
        }
    }

    private fun completeCurrentEntry() {
        currentPeer?.let {
            parsedPeers.add(it)
        }

        currentInterface = currentInterface.copy(peers = parsedPeers)
    }

    private fun resetParser() {
        currentInterface = InterfaceConfig("", 0, emptyList(), null)
        currentPeer = null
        parsedPeers.clear()
        parserStateMachine = createStateMachine()

    }

    private fun createStateMachine() = StateMachine.create {
        initialState(WgEntryParseState.Idle)

        state<WgEntryParseState.Idle> {
            on<WgEntryParseEvent.MatchInterface> {
                transitionTo(WgEntryParseState.InInterface)
            }
            on<WgEntryParseEvent.MatchPeer> {
                transitionTo(WgEntryParseState.InPeers)
            }
            on<WgEntryParseEvent.MatchKV> {
                // 在Idle状态下遇到KV，记录错误
                transitionTo(WgEntryParseState.Error, WgEntryParseSideEffect.RecordError)
            }
        }

        state<WgEntryParseState.InInterface> {
            on<WgEntryParseEvent.MatchKV> {
                transitionTo(WgEntryParseState.InInterface, WgEntryParseSideEffect.CollectInterfaceProperty)
            }
            on<WgEntryParseEvent.MatchPeer> {
                // 完成当前Interface，开始新的Peer
                transitionTo(WgEntryParseState.InPeers, WgEntryParseSideEffect.Complete)
            }
            on<WgEntryParseEvent.MatchInterface> {
                // 新的Interface开始，完成当前Interface
                transitionTo(WgEntryParseState.InInterface, WgEntryParseSideEffect.Complete)
            }
            on<WgEntryParseEvent.MatchEndpoint> {
                // 文件结束
                transitionTo(WgEntryParseState.Idle, WgEntryParseSideEffect.Complete)
            }
        }

        state<WgEntryParseState.InPeers> {
            on<WgEntryParseEvent.MatchKV> {
                transitionTo(WgEntryParseState.InPeers, WgEntryParseSideEffect.CollectPeerProperty)
            }
            on<WgEntryParseEvent.MatchInterface> {
                // 新的Interface开始，完成当前Peer
                transitionTo(WgEntryParseState.InInterface, WgEntryParseSideEffect.Complete)
            }
            on<WgEntryParseEvent.MatchPeer> {
                // 新的Peer开始，完成当前Peer
                transitionTo(WgEntryParseState.InPeers, WgEntryParseSideEffect.Complete)
            }
            on<WgEntryParseEvent.MatchEndpoint> {
                // 文件结束
                transitionTo(WgEntryParseState.Idle, WgEntryParseSideEffect.Complete)
            }
        }

        state<WgEntryParseState.Error> {
            on<WgEntryParseEvent.MatchInterface> {
                transitionTo(WgEntryParseState.InInterface)
            }
            on<WgEntryParseEvent.MatchPeer> {
                transitionTo(WgEntryParseState.InPeers)
            }
        }

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
            val event = validTransition.event
            when (validTransition.sideEffect) {
                WgEntryParseSideEffect.CollectInterfaceProperty, WgEntryParseSideEffect.CollectPeerProperty -> if (event is WgEntryParseEvent.MatchKV) {
                    handleKeyValue(event.key, event.value, validTransition.toState)
                }
                WgEntryParseSideEffect.Complete -> {
                    completeCurrentEntry()
                }
                WgEntryParseSideEffect.RecordError -> {

                }
            }
        }
    }

}


private sealed class WgEntryParseState {
    object Idle : WgEntryParseState()
    object InInterface : WgEntryParseState() {
        var currentInterface: InterfaceConfig =
            InterfaceConfig("", 0, emptyList(), null)
    }

    object InPeers : WgEntryParseState() {
        var currentPeer: PeerConfig? = null
    }

    object Error : WgEntryParseState()
}

private sealed class WgEntryParseEvent {
    object MatchInterface : WgEntryParseEvent()
    object MatchPeer : WgEntryParseEvent()
    data class MatchKV(
        val key: String,
        val value: String
    ) : WgEntryParseEvent()

    object MatchEndpoint : WgEntryParseEvent()
    object MatchUnknown : WgEntryParseEvent()
}

private sealed class WgEntryParseSideEffect {
    object CollectInterfaceProperty : WgEntryParseSideEffect()
    object CollectPeerProperty : WgEntryParseSideEffect()
    object Complete : WgEntryParseSideEffect()
    object RecordError : WgEntryParseSideEffect()
}

