package com.leojay.simplewgad.util

import com.leojay.simplewgad.model.WgEntry
import com.tinder.StateMachine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WgEntryParser {
    private val logger = LoggerFactory.getLogger(WgEntryParser::class.java)

    private val parserStateMachine = StateMachine.create<WgEntryParseState, WgEntryParseEvent, WgEntryParseSideEffect> {
        initialState(WgEntryParseState.Idle)
        state<WgEntryParseState.Idle> {
            on<WgEntryParseEvent.MatchInterface> {
                transitionTo(WgEntryParseState.InInterface)
            }
            on<WgEntryParseEvent.MatchPeer>{
                transitionTo(WgEntryParseState.InPeers)
            }
        }
        state<WgEntryParseState.InInterface> {
            on<WgEntryParseEvent.MatchKV>{
                transitionTo(WgEntryParseState.InInterface)
            }
            on<WgEntryParseEvent.MatchPeer>{
                transitionTo(WgEntryParseState.InPeers)
            }
        }
        state<WgEntryParseState.InPeers>{
            on<WgEntryParseEvent.MatchKV>{
                transitionTo(WgEntryParseState.InPeers)
            }
            on<WgEntryParseEvent.MatchInterface>{
                transitionTo(WgEntryParseState.InInterface)
            }
        }
    }

    fun parseWgDump(output: List<String>): List<WgEntry> {
        return output.mapNotNull { line ->
            val parts = line.split('\t')
            when (parts.size) {
                5 -> WgEntry.Interface(
                    name = parts[0],
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

    fun parseWgConfigFile(configContent : String) : List<WgEntry> {
        val lines = configContent.lines()


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

}

sealed class WgEntryParseSideEffect {
    object CollectInterfaceProperty : WgEntryParseSideEffect()
    object CollectPeerProperty : WgEntryParseSideEffect()
    object Complete : WgEntryParseSideEffect()
    object RecordError : WgEntryParseSideEffect()
}

