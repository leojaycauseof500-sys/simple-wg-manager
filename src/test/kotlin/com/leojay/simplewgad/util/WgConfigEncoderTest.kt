
import com.leojay.simplewgad.model.InterfaceConfig
import com.leojay.simplewgad.model.PeerConfig
import com.leojay.simplewgad.util.WgConfigEncoder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested


class WgConfigEncoderTest {

    private var wgConfigEncoder: WgConfigEncoder = WgConfigEncoder()

    @Nested
    @DisplayName("encodeToConfig 方法测试")
    inner class EncodeToConfigTest {

        @Test
        @DisplayName("正常编码接口配置")
        fun `should encode interface config correctly`() {
            val interfaceConfig = InterfaceConfig(
                privateKey = "private+key+peer+44+chars+base64+encoded++==",
                listenPort = 51820,
                address = listOf("10.0.0.1/24"),
                dns = listOf("8.8.8.8", "1.1.1.1"),
                peers = mutableListOf(
                    PeerConfig(
                        publicKey = "public+key+peer+44+chars+base64+encoded+++==",
                        allowedIPs = listOf("10.0.0.2/32"),
                        endpoint = "example.com:51820",
                        persistentKeepalive = 25
                    )
                )
            )

            val result = wgConfigEncoder.encodeToConfig(interfaceConfig)
            assertTrue(result.isSuccess)

            val config = result.getOrThrow()
            assertTrue(config.contains("[Interface]"))
            assertTrue(config.contains("PrivateKey = private+key+peer+44+chars+base64+encoded++=="))
            assertTrue(config.contains("Address = 10.0.0.1/24"))
            assertTrue(config.contains("ListenPort = 51820"))
            assertTrue(config.contains("DNS = 8.8.8.8,1.1.1.1"))
            assertTrue(config.contains("[Peer]"))
            assertTrue(config.contains("PublicKey = public+key+peer+44+chars+base64+encoded+++=="))
            assertTrue(config.contains("AllowedIPs = 10.0.0.2/32"))
            assertTrue(config.contains("Endpoint = example.com:51820"))
            assertTrue(config.contains("PersistentKeepalive = 25"))
        }

        @Test
        @DisplayName("处理缺少PrivateKey的情况")
        fun `should fail with missing private key`() {
            val interfaceConfig = InterfaceConfig(
                privateKey = "",
                listenPort = 51820,
                address = listOf("10.0.0.1/24"),
                peers = mutableListOf()
            )

            val result = wgConfigEncoder.encodeToConfig(interfaceConfig)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Interface 缺少 PrivateKey") == true)
        }

        @Test
        @DisplayName("处理无效的IP地址格式")
        fun `should fail with invalid ip address format`() {
            val interfaceConfig = InterfaceConfig(
                privateKey = "private+key+peer+44+chars+base64+encoded++==",
                listenPort = 51820,
                address = listOf("10.0.0.1"), // 缺少子网掩码
                peers = mutableListOf()
            )

            val result = wgConfigEncoder.encodeToConfig(interfaceConfig)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Address 格式不正确") == true)
        }

        @Test
        @DisplayName("处理多个Peer的情况")
        fun `should handle multiple peers correctly`() {
            val interfaceConfig = InterfaceConfig(
                privateKey = "private+key+peer+44+chars+base64+encoded++==",
                listenPort = 51820,
                address = listOf("10.0.0.1/24"),
                peers = mutableListOf(
                    PeerConfig(
                        publicKey = "public+key+peer1+44+chars+base64+encoded++==",
                        allowedIPs = listOf("10.0.0.2/32")
                    ),
                    PeerConfig(
                        publicKey = "public+key+peer2+44+chars+base64+encoded++==",
                        allowedIPs = listOf("10.0.0.3/32"),
                        endpoint = "192.168.1.100:51820"
                    )
                )
            )

            val result = wgConfigEncoder.encodeToConfig(interfaceConfig)
            assertTrue(result.isSuccess)

            val config = result.getOrThrow()
            val peerSections = config.split("[Peer]")
            assertEquals(3, peerSections.size) // 包含开头的空部分
        }
    }

    @Nested
    @DisplayName("generateClientConfig 方法测试")
    inner class GenerateClientConfigTest {

        @Test
        @DisplayName("正常生成客户端配置")
        fun `should generate client config correctly`() {
            val result = wgConfigEncoder.generateClientConfig(
                clientName = "test-client",
                privateKey = "private+key+peer+44+chars+base64+encoded++==",
                serverPublicKey = "server+publickey+44+chars+base64+encoded++==",
                serverEndpoint = "vpn.example.com:51820",
                allowedIps = "10.0.0.2/32",
                dnsServers = listOf("8.8.8.8", "1.1.1.1")
            )

            assertTrue(result.isSuccess)
            val config = result.getOrThrow()

            assertTrue(config.contains("# Client: test-client"))
            assertTrue(config.contains("PrivateKey = private+key+peer+44+chars+base64+encoded++=="))
            assertTrue(config.contains("Address = 10.0.0.2/32"))
            assertTrue(config.contains("DNS = 8.8.8.8,1.1.1.1"))
            assertTrue(config.contains("PublicKey = server+publickey+44+chars+base64+encoded++=="))
            assertTrue(config.contains("Endpoint = vpn.example.com:51820"))
            assertTrue(config.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
            assertTrue(config.contains("PersistentKeepalive = 25"))
        }

        @Test
        @DisplayName("处理无效的服务器Endpoint")
        fun `should fail with invalid server endpoint`() {
            val result = wgConfigEncoder.generateClientConfig(
                clientName = "test-client",
                privateKey = "private+key+peer+44+chars+base64+encoded++==",
                serverPublicKey = "server+publickey+44+chars+base64+encoded++==",
                serverEndpoint = "invalid-endpoint", // 缺少端口
                allowedIps = "10.0.0.2/32"
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("服务器 Endpoint 格式不正确") == true)
        }
    }

    @Nested
    @DisplayName("updateServerConfig 方法测试")
    inner class UpdateServerConfigTest {

        @Test
        @DisplayName("正常更新服务器配置")
        fun `should update server config correctly`() {
            val existingConfig = """
                [Interface]
                PrivateKey = server+privatekey+44+chars+base64+encoded+==
                Address = 10.0.0.1/24
                ListenPort = 51820

                [Peer]
                PublicKey = public+key+peer+44+chars+base64+encoded+++==
                AllowedIPs = 10.0.0.2/32
            """.trimIndent()

            val newPeer = PeerConfig(
                publicKey = "new+peer+public+key+44+chars+base64+encoded=",
                allowedIPs = listOf("10.0.0.3/32"),
                endpoint = "client.example.com:51820"
            )

            val result = wgConfigEncoder.updateServerConfig(existingConfig, newPeer)
            assertTrue(result.isSuccess)

            val updatedConfig = result.getOrThrow()
            assertTrue(updatedConfig.contains("PublicKey = new+peer+public+key+44+chars+base64+encoded="))
            assertTrue(updatedConfig.contains("AllowedIPs = 10.0.0.3/32"))
            assertTrue(updatedConfig.contains("Endpoint = client.example.com:51820"))
        }

        @Test
        @DisplayName("在空配置中添加Peer")
        fun `should add peer to empty config`() {
            val existingConfig = """
                [Interface]
                PrivateKey =server+privatekey+44+chars+base64+encoded+==
                Address = 10.0.0.1/24
            """.trimIndent()

            val newPeer = PeerConfig(
                publicKey = "new+peer+public+key+44+chars+base64+encoded=",
                allowedIPs = listOf("10.0.0.2/32")
            )

            val result = wgConfigEncoder.updateServerConfig(existingConfig, newPeer)
            assertTrue(result.isSuccess)

            val updatedConfig = result.getOrThrow()
            val lines = updatedConfig.lines()
            assertTrue(lines.contains("[Peer]"))
            assertTrue(lines.contains("PublicKey = new+peer+public+key+44+chars+base64+encoded="))
        }
    }
}
