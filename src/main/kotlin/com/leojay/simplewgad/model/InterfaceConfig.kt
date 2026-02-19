package com.leojay.simplewgad.model


/**
 * 接口配置数据类
 */
data class InterfaceConfig(
    val interfaceName: String,
    val publicKey: String,
    val listenPort: Int,
    val peerCount: Int,
    val peers: List<PeerInfo>
)

