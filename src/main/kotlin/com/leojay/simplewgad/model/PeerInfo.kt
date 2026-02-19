package com.leojay.simplewgad.model
/**
 * 对等端信息数据类
 */
data class PeerInfo(
    val publicKey: String,
    val endpoint: String,
    val allowedIps: List<String>,
    val latestHandshake: Long,
    val transferRx: Long,
    val transferTx: Long
)

