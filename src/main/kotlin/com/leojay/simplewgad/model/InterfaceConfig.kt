package com.leojay.simplewgad.model

/**
 * 接口配置数据类
 */
data class InterfaceConfig(
    val privateKey: String,
    val listenPort: Int,
    val address: List<String>,
    val dns: List<String>? = null,
    val peers: MutableList<PeerConfig> = mutableListOf(),
)

data class PeerConfig(
    val publicKey: String,
    val presharedKey: String? = null,
    val allowedIPs: List<String>,
    val persistentKeepalive: Int? = null,
    val endpoint: String? = null       // 配置中的初始端点
)
