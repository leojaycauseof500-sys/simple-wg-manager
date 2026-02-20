package com.leojay.simplewgad.util

import com.leojay.simplewgad.model.WgConfig
import com.leojay.simplewgad.model.WgEntry
import com.leojay.simplewgad.util.constant.Symbols
import com.tinder.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WgEntryParser {
    private val logger = LoggerFactory.getLogger(WgEntryParser::class.java)


    // 用于收集解析过程中的数据
    private var currentInterface: WgConfig.InterfaceConfig? = null
    private var currentPeer: WgConfig.PeerConfig? = null
    private val parsedEntries = mutableListOf<WgConfig>()

    private val parserStateMachine = StateMachine.create {
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
    }

    fun parseWgDump(output: List<String>): List<WgEntry> {
        return output.mapNotNull { line ->
            val parts = line.split('\t')
            when (parts.size) {
                5 -> WgEntry.Interface(
                    name = parts[0],
                    //忽略私钥字段
                    publicKey = parts[2],
                    listenPort = parts[3].toIntOrNull() ?: 0
                )
                9 -> WgEntry.Peer(
                    interfaceName = parts[0],
                    publicKey = parts[1],
                    endpoint = parts[3].takeIf { it != "(none)" } ?: "",
                    allowedIps = parts[4].split(','),
                    latestHandshake = parts[5].toLongOrNull() ?: 0L,
                    transferRx = parts[6].toLongOrNull() ?: 0L,
                    transferTx = parts[7].toLongOrNull() ?: 0L
                )
                else -> null // 跳过异常行
            }
        }
    }

    fun parseWgConfigFile(configContent: String): List<WgConfig> {
        // 重置状态
        resetParser()

        val lines = configContent.lines()
        lines.forEach { line ->
            parseLine(line.trim())
        }

        // 处理最后一个条目
        completeCurrentEntry()

        return parsedEntries.toList()
    }

    private fun parseLine(line: String) {
        when {
            line.startsWith("[Interface]") -> {
                parserStateMachine.transition(WgEntryParseEvent.MatchInterface)
                // 开始新的Interface
                currentInterface = WgConfig.InterfaceConfig("","",0, emptyList(),null, emptyList())
            }
            line.startsWith("[Peer]") -> {
                parserStateMachine.transition(WgEntryParseEvent.MatchPeer)
                // 开始新的Peer
                currentPeer = WgConfig.PeerConfig(publicKey = "", allowedIPs = emptyList())
            }
            line.isEmpty() || line.startsWith("#") -> {
                // 跳过空行和注释
            }
            line.contains("=") -> {
                val (key, value) = line.split("=", limit = 2).map { it.trim() }
                handleKeyValue(key, value)
            }
            else -> {
                parserStateMachine.transition(WgEntryParseEvent.MatchUnknown)
            }
        }
    }

    private fun handleKeyValue(key: String, value: String) {
        parserStateMachine.transition(WgEntryParseEvent.MatchKV)

        when (parserStateMachine.state) {
            is WgEntryParseState.InInterface -> {
                currentInterface = currentInterface?.let { interfaceConfig ->
                    when (key.lowercase()) {
                        "privatekey" -> interfaceConfig.copy(privateKey = value)
                        "address" -> interfaceConfig.copy(address = value.split(Symbols.COMMA))
                        "listenport" -> interfaceConfig.copy(listenPort = value.toIntOrNull() ?: 0)
                        "dns" -> interfaceConfig.copy(dns = value.split(Symbols.COMMA))
                        else -> interfaceConfig
                    }
                }
            }
            is WgEntryParseState.InPeers -> {
                currentPeer = currentPeer?.let { peerConfig ->
                    when (key.lowercase()) {
                        "publickey" -> peerConfig.copy(publicKey = value)
                        "allowedips" -> peerConfig.copy(allowedIPs = value.split(Symbols.COMMA))
                        "endpoint" -> peerConfig.copy(endpoint = value)
                        "persistentkeepalive" -> peerConfig.copy(persistentKeepalive = value.toIntOrNull())
                        "presharedKey" -> peerConfig.copy(presharedKey = value)
                        else -> peerConfig
                    }
                }
            }
            else -> {
                logger.warn("Unexpected key-value pair in state: ${parserStateMachine.state}")
            }
        }
    }

    private fun completeCurrentEntry() {
        currentInterface?.let {
            // 需要从PrivateKey生成PublicKey
            // 这里简化处理，实际需要调用wg命令
            parsedEntries.add(it)
            currentInterface = null
        }

        currentPeer?.let {
            // 需要设置interfaceName
            val interfaceName = currentInterface?.name ?: "unknown"
            parsedEntries.add(it.copy(interfaceName = interfaceName))
            currentPeer = null
        }
    }

    private fun resetParser() {
        currentInterface = null
        currentPeer = null
        parsedEntries.clear()
        // 重置状态机到初始状态
        // 注意：Tinder StateMachine没有直接的reset方法
        // 可能需要重新创建状态机或使用其他方式
    }

}


sealed class WgEntryParseState {
    object Idle : WgEntryParseState()
    object InInterface : WgEntryParseState()
    object InPeers : WgEntryParseState()
    object Error : WgEntryParseState()
}

sealed class WgEntryParseEvent {
    object MatchInterface : WgEntryParseEvent()
    object MatchPeer : WgEntryParseEvent()
    object MatchKV : WgEntryParseEvent()
    object MatchEndpoint : WgEntryParseEvent()
    object MatchUnknown : WgEntryParseEvent()
}

sealed class WgEntryParseSideEffect {
    object CollectInterfaceProperty : WgEntryParseSideEffect()
    object CollectPeerProperty : WgEntryParseSideEffect()
    object Complete : WgEntryParseSideEffect()
    object RecordError : WgEntryParseSideEffect()
}

