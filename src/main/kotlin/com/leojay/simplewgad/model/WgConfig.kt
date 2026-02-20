package com.leojay.simplewgad.model
/**
 * 从配置文件（如wg0.conf）获取wg的配置信息
 */
sealed class WgConfig{
    /**
     * 接口配置数据类
     */
    data class InterfaceConfig(
        val interfaceName: String,
        val privateKey: String,
        val listenPort: Int,
        val address: List<String>,
        val dns: List<String>? = null,
        val peers: List<PeerConfig>
    ) : WgConfig()

    /**
     * 对等端信息数据类
     */
    data class PeerConfig(
        val publicKey: String,
        val presharedKey: String? = null,
        val allowedIPs: List<String>,
        val persistentKeepalive: Int? = null,
        val endpoint: String? = null       // 配置中的初始端点
    ) : WgConfig()
}