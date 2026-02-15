package com.leojay.simplewgad.util

import com.leojay.simplewgad.model.WgEntry
import org.springframework.stereotype.Component

@Component
class WgEntryParser {
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
}
