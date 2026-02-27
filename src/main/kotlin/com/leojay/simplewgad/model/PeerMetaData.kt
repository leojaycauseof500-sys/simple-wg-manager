package com.leojay.simplewgad.model

import com.leojay.simplewgad.util.constant.Symbols

data class PeerMetaData(
    var server: ServerMetaData,
    var clients: Map<String, ClientMetaData>
) {
    fun toInterfaceConfig(
        listenPort: Int = 51820,
        dns: List<String>? = null,
    ): InterfaceConfig = InterfaceConfig(
        privateKey = server.privateKey,
        listenPort = listenPort,
        address = listOf(server.address),
        dns = dns,
        peers = clients.values
            .filter { it.enabled }
            .map { client ->
                PeerConfig(
                    publicKey = client.publicKey,
                    presharedKey = client.presharedKey,
                    allowedIPs = client.address.split(Symbols.COMMA),
                    persistentKeepalive = 25, // 默认值
                    endpoint = null // 客户端端点通常为空，由客户端连接时确定
                )
            }.toMutableList()
    )

}

data class ClientMetaData(
    val name: String,
    val privateKey: String,
    val presharedKey: String? = null,
    val publicKey: String,
    val address: String,
    val enabled: Boolean = false
)

data class ServerMetaData(
    val privateKey: String,
    val publicKey: String ,
    val address: String
)
