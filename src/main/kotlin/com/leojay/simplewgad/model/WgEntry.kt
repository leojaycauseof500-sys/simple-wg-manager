package com.leojay.simplewgad.model

/**
 * 通过 "wg show all dump"获取的实时状态
 */
sealed class WgEntry {
    // 5个字段：interface, private_key, public_key, listen_port, fwmark
    data class Interface(
        val name: String,
        val publicKey: String,
        val listenPort: Int
    ) : WgEntry()

    // 9个字段：interface, peer_pubkey, preshared_key, endpoint, allowed_ips, latest_handshake, rx, tx, keepalive
    data class Peer(
        val interfaceName: String,
        val publicKey: String,
        val endpoint: String,
        val allowedIps: List<String>,
        val latestHandshake: Long, // Unix Timestamp
        val transferRx: Long,
        val transferTx: Long
    ) : WgEntry()
}

