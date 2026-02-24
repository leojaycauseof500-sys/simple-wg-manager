package com.leojay.simplewgad.util

import com.leojay.simplewgad.model.InterfaceConfig
import com.leojay.simplewgad.model.PeerConfig
import com.leojay.simplewgad.util.constant.Symbols
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class WgConfigEncoder {
    private val logger = LoggerFactory.getLogger(WgConfigEncoder::class.java)

    /**
     * 将 InterfaceConfig 编码为 WireGuard 配置文件字符串
     */
    fun encodeToConfig(interfaceConfig: InterfaceConfig): Result<String> = runCatching {
        val configBuilder = StringBuilder()

        // 添加 [Interface] 部分
        configBuilder.append("[Interface]\n")
        encodeInterfaceSection(interfaceConfig, configBuilder)

        // 添加 [Peer] 部分
        if (interfaceConfig.peers.isNotEmpty()) {
            configBuilder.append("\n")
            interfaceConfig.peers.forEachIndexed { index, peerConfig ->
                encodePeerSection(peerConfig, configBuilder, index)
                if (index < interfaceConfig.peers.size - 1) {
                    configBuilder.append("\n")
                }
            }
        }

        configBuilder.toString()
    }.onFailure { exception ->
        logger.error("编码 WireGuard 配置失败: ${exception.message}", exception)
    }

    /**
     * 编码 [com.leojay.simplewgad.model.WgEntry.Interface] 部分
     */
    private fun encodeInterfaceSection(
        interfaceConfig: InterfaceConfig,
        builder: StringBuilder
    ) {
        // 验证 PrivateKey
        require(interfaceConfig.privateKey.isNotBlank()) { "Interface 缺少 PrivateKey" }
        require(interfaceConfig.privateKey.matches(Regex("^[A-Za-z0-9+/=]{44}\$"))) {
            "PrivateKey 格式不正确: ${interfaceConfig.privateKey}"
        }
        builder.append("PrivateKey = ${interfaceConfig.privateKey}\n")

        // 验证 Address
        require(interfaceConfig.address.isNotEmpty()) { "Interface 缺少 Address" }
        interfaceConfig.address.forEach { address ->
            require(address.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}\$")) ||
                address.matches(Regex("^[a-fA-F0-9:]+/\\d{1,3}\$"))) {
                "Address 格式不正确: $address"
            }
        }
        builder.append("Address = ${interfaceConfig.address.joinToString(Symbols.COMMA)}\n")

        // 添加 ListenPort（如果大于0）
        if (interfaceConfig.listenPort > 0) {
            require(interfaceConfig.listenPort in 1..65535) {
                "ListenPort 超出范围: ${interfaceConfig.listenPort}"
            }
            builder.append("ListenPort = ${interfaceConfig.listenPort}\n")
        }

        // 添加 DNS（如果存在）
        interfaceConfig.dns?.takeIf { it.isNotEmpty() }?.let { dnsServers ->
            dnsServers.forEach { dns ->
                require(dns.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\$")) ||
                    dns.matches(Regex("^[a-fA-F0-9:]+$"))) {
                    "DNS 服务器格式不正确: $dns"
                }
            }
            builder.append("DNS = ${dnsServers.joinToString(Symbols.COMMA)}\n")
        }
    }

    /**
     * 编码 [com.leojay.simplewgad.model.WgEntry.Peer] 部分
     */
    private fun encodePeerSection(
        peerConfig: PeerConfig,
        builder: StringBuilder,
        peerIndex: Int
    ) {
        builder.append("[Peer]\n")

        // 验证 PublicKey
        require(peerConfig.publicKey.isNotBlank()) { "Peer $peerIndex 缺少 PublicKey" }
        require(peerConfig.publicKey.matches(Regex("^[A-Za-z0-9+/=]{44}\$"))) {
            "Peer $peerIndex PublicKey 格式不正确: ${peerConfig.publicKey}"
        }
        builder.append("PublicKey = ${peerConfig.publicKey}\n")

        // 验证 AllowedIPs
        require(peerConfig.allowedIPs.isNotEmpty()) { "Peer $peerIndex 缺少 AllowedIPs" }
        peerConfig.allowedIPs.forEach { ip ->
            require(ip.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}\$")) ||
                ip.matches(Regex("^[a-fA-F0-9:]+/\\d{1,3}\$"))) {
                "Peer $peerIndex AllowedIPs 格式不正确: $ip"
            }
        }
        builder.append("AllowedIPs = ${peerConfig.allowedIPs.joinToString(Symbols.COMMA)}\n")

        // 添加 PresharedKey（如果存在）
        peerConfig.presharedKey?.takeIf { it.isNotBlank() }?.let { presharedKey ->
            require(presharedKey.matches(Regex("^[A-Za-z0-9+/=]{44}\$"))) {
                "Peer $peerIndex PresharedKey 格式不正确: $presharedKey"
            }
            builder.append("PresharedKey = $presharedKey\n")
        }

        // 添加 Endpoint（如果存在）
        peerConfig.endpoint?.takeIf { it.isNotBlank() }?.let { endpoint ->
            require(endpoint.matches(Regex("^[a-zA-Z0-9.-]+:\\d{1,5}\$")) ||
                endpoint.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}\$"))) {
                "Peer $peerIndex Endpoint 格式不正确: $endpoint"
            }
            builder.append("Endpoint = $endpoint\n")
        }

        // 添加 PersistentKeepalive（如果存在）
        peerConfig.persistentKeepalive?.let { keepalive ->
            require(keepalive in 0..65535) {
                "Peer $peerIndex PersistentKeepalive 超出范围: $keepalive"
            }
            builder.append("PersistentKeepalive = $keepalive\n")
        }
    }


    /**
     * 生成客户端配置文件
     */
    fun generateClientConfig(
        clientName: String,
        privateKey: String,
        serverPublicKey: String,
        serverEndpoint: String,
        allowedIps: String,
        dnsServers: List<String>? = null
    ): Result<String> = runCatching {
        // 验证客户端配置参数
        require(clientName.isNotBlank()) { "客户端名称不能为空" }
        require(privateKey.matches(Regex("^[A-Za-z0-9+/=]{44}\$"))) {
            "客户端 PrivateKey 格式不正确"
        }
        require(serverPublicKey.matches(Regex("^[A-Za-z0-9+/=]{44}\$"))) {
            "服务器 PublicKey 格式不正确"
        }
        require(serverEndpoint.matches(Regex("^[a-zA-Z0-9.-]+:\\d{1,5}\$")) ||
            serverEndpoint.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}\$"))) {
            "服务器 Endpoint 格式不正确: $serverEndpoint"
        }
        require(allowedIps.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}/\\d{1,2}\$")) ||
            allowedIps.matches(Regex("^[a-fA-F0-9:]+/\\d{1,3}\$"))) {
            "AllowedIPs 格式不正确: $allowedIps"
        }

        val configBuilder = StringBuilder()

        // 客户端 [Interface] 部分
        configBuilder.append("# Client: $clientName\n")
        configBuilder.append("[Interface]\n")
        configBuilder.append("PrivateKey = $privateKey\n")
        configBuilder.append("Address = ${allowedIps.replace("/32", "")}/32\n")

        dnsServers?.takeIf { it.isNotEmpty() }?.let { dns ->
            dns.forEach { dnsServer ->
                require(dnsServer.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\$")) ||
                    dnsServer.matches(Regex("^[a-fA-F0-9:]+$"))) {
                    "DNS 服务器格式不正确: $dnsServer"
                }
            }
            configBuilder.append("DNS = ${dns.joinToString(Symbols.COMMA)}\n")
        }

        // 客户端 [Peer] 部分（服务器）
        configBuilder.append("\n[Peer]\n")
        configBuilder.append("PublicKey = $serverPublicKey\n")
        configBuilder.append("Endpoint = $serverEndpoint\n")
        configBuilder.append("AllowedIPs = 0.0.0.0/0, ::/0\n")
        configBuilder.append("PersistentKeepalive = 25\n")

        configBuilder.toString()
    }.onFailure { exception ->
        logger.error("生成客户端配置失败: ${exception.message}", exception)
    }

    /**
     * 更新服务器配置文件（添加新的 Peer）
     */
    fun updateServerConfig(
        existingConfig: String,
        newPeer: PeerConfig
    ): Result<String> = runCatching {
        // 验证现有配置
        require(existingConfig.isNotBlank()) { "现有配置不能为空" }

        // 验证新 Peer
        require(newPeer.publicKey.isNotBlank()) { "新 Peer 缺少 PublicKey" }
        require(newPeer.allowedIPs.isNotEmpty()) { "新 Peer 缺少 AllowedIPs" }

        val lines = existingConfig.lines().toMutableList()

        // 查找最后一个 [Peer] 部分的位置
        var lastPeerIndex = -1
        lines.forEachIndexed { index, line ->
            if (line.trim() == "[Peer]") {
                lastPeerIndex = index
            }
        }

        // 如果没有找到 [Peer] 部分，在文件末尾添加
        val insertIndex = if (lastPeerIndex == -1) {
            // 查找 [Interface] 部分的结束位置
            val interfaceEndIndex = lines.indexOfLast {
                it.trim().startsWith("[Interface]") || it.trim().startsWith("#")
            }
            if (interfaceEndIndex == -1) lines.size else interfaceEndIndex + 1
        } else {
            // 找到最后一个 [Peer] 的结束位置
            var peerEndIndex = lastPeerIndex
            while (peerEndIndex < lines.size - 1 &&
                !lines[peerEndIndex + 1].trim().startsWith("[") &&
                !lines[peerEndIndex + 1].trim().startsWith("#")) {
                peerEndIndex++
            }
            peerEndIndex + 1
        }

        // 确保在插入前有空行分隔
        if (insertIndex > 0 && lines[insertIndex - 1].isNotBlank()) {
            lines.add(insertIndex, "")
        }

        // 构建新 Peer 的配置行
        val newPeerLines = mutableListOf<String>()
        newPeerLines.add("[Peer]")
        newPeerLines.add("PublicKey = ${newPeer.publicKey}")
        newPeerLines.add("AllowedIPs = ${newPeer.allowedIPs.joinToString(Symbols.COMMA)}")

        newPeer.endpoint?.takeIf { it.isNotBlank() }?.let { endpoint ->
            newPeerLines.add("Endpoint = $endpoint")
        }

        newPeer.presharedKey?.takeIf { it.isNotBlank() }?.let { presharedKey ->
            newPeerLines.add("PresharedKey = $presharedKey")
        }

        newPeer.persistentKeepalive?.let { keepalive ->
            newPeerLines.add("PersistentKeepalive = $keepalive")
        }

        // 插入新 Peer 配置
        lines.addAll(insertIndex, newPeerLines)

        lines.joinToString("\n")
    }.onFailure { exception ->
        logger.error("更新服务器配置失败: ${exception.message}", exception)
    }
}
