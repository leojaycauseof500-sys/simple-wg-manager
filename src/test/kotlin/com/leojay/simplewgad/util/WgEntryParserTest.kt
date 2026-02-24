package com.leojay.simplewgad.util

import com.leojay.simplewgad.model.WgEntry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class WgEntryParserTest {

    private var wgEntryParser: WgEntryParser = WgEntryParser()

    @Nested
    @DisplayName("parseWgDump 方法测试")
    inner class ParseWgDumpTest {

        @Test
        @DisplayName("正常解析接口和Peer数据")
        fun `should parse interface and peer entries correctly`() {
            // 准备测试数据
            val wgDumpOutput = listOf(
                // 接口行 (5个字段)
                "wg0\tprivate_key\tpublic_key_interface\t51820\t0",
                // Peer行 (9个字段)
                "wg0\tpublic_key_peer\tpreshared_key\t192.168.1.100:51820\t10.0.0.2/32\t1698765432\t1024\t2048\t25"
            )

            // 执行解析
            val result = wgEntryParser.parseWgDump(wgDumpOutput)

            // 验证结果
            assertTrue(result.isSuccess)
            val entries = result.getOrThrow()
            assertEquals(2, entries.size)

            // 验证接口
            val interfaceEntry = entries[0] as WgEntry.Interface
            assertEquals("wg0", interfaceEntry.name)
            assertEquals("public_key_interface", interfaceEntry.publicKey)
            assertEquals(51820, interfaceEntry.listenPort)

            // 验证Peer
            val peerEntry = entries[1] as WgEntry.Peer
            assertEquals("wg0", peerEntry.interfaceName)
            assertEquals("public_key_peer", peerEntry.publicKey)
            assertEquals("192.168.1.100:51820", peerEntry.endpoint)
            assertEquals(listOf("10.0.0.2/32"), peerEntry.allowedIps)
            assertEquals(1698765432L, peerEntry.latestHandshake)
            assertEquals(1024L, peerEntry.transferRx)
            assertEquals(2048L, peerEntry.transferTx)
        }

        @Test
        @DisplayName("处理空端点(none)的情况")
        fun `should handle none endpoint correctly`() {
            val wgDumpOutput = listOf(
                "wg0\tprivate_key\tpublic_key_interface\t51820\t0",
                "wg0\tpublic_key_peer\tpreshared_key\t(none)\t10.0.0.2/32\t0\t0\t0\t0"
            )

            val result = wgEntryParser.parseWgDump(wgDumpOutput)
            assertTrue(result.isSuccess)
            val peerEntry = (result.getOrThrow()[1] as WgEntry.Peer)
            assertEquals("", peerEntry.endpoint) // (none) 应该被转换为空字符串
        }

        @Test
        @DisplayName("处理多个Allowed IPs")
        fun `should handle multiple allowed IPs`() {
            val wgDumpOutput = listOf(
                "wg0\tprivate_key\tpublic_key_interface\t51820\t0",
                "wg0\tpublic_key_peer\tpreshared_key\t192.168.1.100:51820\t10.0.0.2/32,10.0.0.3/32\t1698765432\t1024\t2048\t25"
            )

            val result = wgEntryParser.parseWgDump(wgDumpOutput)
            assertTrue(result.isSuccess)
            val peerEntry = (result.getOrThrow()[1] as WgEntry.Peer)
            assertEquals(listOf("10.0.0.2/32", "10.0.0.3/32"), peerEntry.allowedIps)
        }

        @Test
        @DisplayName("处理空输入")
        fun `should fail with empty input`() {
            val result = wgEntryParser.parseWgDump(emptyList())
            assertTrue(result.isFailure)
            assertEquals("输出内容为空", result.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("处理只有空白行的输入")
        fun `should fail with only blank lines`() {
            val result = wgEntryParser.parseWgDump(listOf("", "  ", "\t"))
            assertTrue(result.isFailure)
            assertEquals("没有有效的输出行", result.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("处理字段数量异常的行")
        fun `should skip lines with wrong field count`() {
            val wgDumpOutput = listOf(
                "wg0\tprivate_key\tpublic_key_interface\t51820\t0", // 正常接口行
                "invalid_line_with_only_3_fields", // 异常行
                "wg0\tpublic_key_peer\tpreshared_key\t192.168.1.100:51820\t10.0.0.2/32\t1698765432\t1024\t2048\t25" // 正常Peer行
            )

            val result = wgEntryParser.parseWgDump(wgDumpOutput)
            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrThrow().size) // 应该只解析出2个有效条目
        }

        @Test
        @DisplayName("处理接口名称为空的情况")
        fun `should handle empty interface name`() {
            val wgDumpOutput = listOf(
                "\tprivate_key\tpublic_key_interface\t51820\t0"
            )

            val result = wgEntryParser.parseWgDump(wgDumpOutput)
            assertTrue(result.isSuccess)
            val interfaceEntry = result.getOrThrow()[0] as WgEntry.Interface
            assertEquals("unknown", interfaceEntry.name)
        }

        @Test
        @DisplayName("处理端口号解析失败的情况")
        fun `should handle invalid port number`() {
            val wgDumpOutput = listOf(
                "wg0\tprivate_key\tpublic_key_interface\tinvalid_port\t0"
            )

            val result = wgEntryParser.parseWgDump(wgDumpOutput)
            assertTrue(result.isSuccess)
            val interfaceEntry = result.getOrThrow()[0] as WgEntry.Interface
            assertEquals(0, interfaceEntry.listenPort) // 解析失败应该返回0
        }
    }

    @Nested
    @DisplayName("parseConfigFile 方法测试")
    inner class ParseConfigFileTest {

        @Test
        @DisplayName("正常解析配置文件")
        fun `should parse config file correctly`() {
            val configContent = """
                [Interface]
                PrivateKey = GCNL0542D7pOCrRuuUQ+TXJO+FhCLP52oZ9JYV7U5Ug=
                Address = 10.0.0.1/24
                ListenPort = 51820
                DNS = 8.8.8.8,1.1.1.1

                [Peer]
                PublicKey = O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=
                AllowedIPs = 10.0.0.2/32
                Endpoint = 192.168.1.100:51820
                PersistentKeepalive = 25

                [Peer]
                PublicKey = O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=
                AllowedIPs = 10.0.0.3/32
            """.trimIndent()

            val result = wgEntryParser.parseConfigFile(configContent)
            assertTrue(result.isSuccess)

            val interfaceConfig = result.getOrThrow()
            assertEquals("GCNL0542D7pOCrRuuUQ+TXJO+FhCLP52oZ9JYV7U5Ug=", interfaceConfig.privateKey)
            assertEquals(listOf("10.0.0.1/24"), interfaceConfig.address)
            assertEquals(51820, interfaceConfig.listenPort)
            assertEquals(listOf("8.8.8.8", "1.1.1.1"), interfaceConfig.dns)
            assertEquals(2, interfaceConfig.peers.size)

            // 验证第一个Peer
            val peer1 = interfaceConfig.peers[0]
            assertEquals("O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=", peer1.publicKey)
            assertEquals(listOf("10.0.0.2/32"), peer1.allowedIPs)
            assertEquals("192.168.1.100:51820", peer1.endpoint)
            assertEquals(25, peer1.persistentKeepalive)

            // 验证第二个Peer
            val peer2 = interfaceConfig.peers[1]
            assertEquals("O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=", peer2.publicKey)
            assertEquals(listOf("10.0.0.3/32"), peer2.allowedIPs)
            assertNull(peer2.endpoint)
        }

        @Test
        @DisplayName("处理空配置文件")
        fun `should fail with empty config content`() {
            val result = wgEntryParser.parseConfigFile("")
            assertTrue(result.isFailure)
            assertEquals("配置文件内容为空", result.exceptionOrNull()?.message)
        }

        @Test
        @DisplayName("处理缺少Interface部分的配置文件")
        fun `should fail with missing interface section`() {
            val configContent = """
                [Peer]
                PublicKey = public_key_peer1
                AllowedIPs = 10.0.0.2/32
            """.trimIndent()

            val result = wgEntryParser.parseConfigFile(configContent)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("配置文件缺少 [Interface] 部分") == true)
        }

        @Test
        @DisplayName("处理缺少必要字段的配置文件")
        fun `should fail with missing required fields`() {
            val configContent = """
                [Interface]
                ListenPort = 51820
                # 缺少 PrivateKey 和 Address
            """.trimIndent()

            val result = wgEntryParser.parseConfigFile(configContent)
            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(
                exception?.message?.contains("Interface 缺少 privateKey") == true ||
                    exception?.message?.contains("Interface 缺少 address") == true
            )
        }

        @Test
        @DisplayName("处理带注释和空行的配置文件")
        fun `should handle config with comments and empty lines`() {
            val configContent = """
                # 这是一个WireGuard配置文件
                [Interface]
                PrivateKey = O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=
                Address = 10.0.0.1/24

                # 第一个客户端
                [Peer]
                PublicKey = O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=
                AllowedIPs = 10.0.0.2/32

                # 第二个客户端
                [Peer]
                PublicKey = O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=
                AllowedIPs = 10.0.0.3/32
            """.trimIndent()

            val result = wgEntryParser.parseConfigFile(configContent)
            assertTrue(result.isSuccess)
            val interfaceConfig = result.getOrThrow()
            assertEquals("O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=", interfaceConfig.privateKey)
            assertEquals(2, interfaceConfig.peers.size)
        }

        @Test
        @DisplayName("处理大小写不敏感的键名")
        fun `should handle case-insensitive keys`() {
            val configContent = """
                [Interface]
                privatekey = O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=
                ADDRESS = 10.0.0.1/24
                listenport = 51820

                [Peer]
                PUBLICKEY = O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=
                allowedips = 10.0.0.2/32
            """.trimIndent()

            val result = wgEntryParser.parseConfigFile(configContent)
            assertTrue(result.isSuccess)
            val interfaceConfig = result.getOrThrow()
            assertEquals("O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=", interfaceConfig.privateKey)
            assertEquals(listOf("10.0.0.1/24"), interfaceConfig.address)
            assertEquals(51820, interfaceConfig.listenPort)
            assertEquals("O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=", interfaceConfig.peers[0].publicKey)
        }

        @Test
        @DisplayName("处理多个Address和DNS")
        fun `should handle multiple addresses and dns`() {
            val configContent = """
                [Interface]
                PrivateKey = O5aBjaJXHuCKzePxy+bcAoupxUuFU4508u1sdluIgzU=
                Address = 10.0.0.1/24,fd00::1/64
                DNS = 8.8.8.8,8.8.4.4,1.1.1.1
            """.trimIndent()

            val result = wgEntryParser.parseConfigFile(configContent)
            assertTrue(result.isSuccess)
            val interfaceConfig = result.getOrThrow()
            assertEquals(listOf("10.0.0.1/24", "fd00::1/64"), interfaceConfig.address)
            assertEquals(listOf("8.8.8.8", "8.8.4.4", "1.1.1.1"), interfaceConfig.dns)
        }
    }

//    @Test
//    @DisplayName("集成测试：解析真实wg dump输出")
//    fun `integration test with real wg dump output`() {
//        // 模拟真实的wg show all dump输出
//        val wgDumpOutput = """
//            wg0\tYAn9YJgV7hLcK7PpLmQ9qXwRzTvUuIxOyPzAaBbCcDdEe=\tuJkLmNoPqRsTuVwXyZzAbCdEfGhIjKlMnOpQrStUvWxYz=\t51820\t0
//            wg0\trStUvWxYzAbCdEfGhIjKlMnOpQrStUvWxYzAbCdEfG=\t(none)\t192.168.1.100:51820 10.0.0.2/32\t1698765432\t1048576\t2097152\t25
//            wg0\ttUvWxYzAbCdEfGhIjKlMnOpQrStUvWxYzAbCdEfGhI=\t(none)\t192.168.1.101:51820\t10.0.0.3/32\t1698765433\t524288\t1048576\t0
//        """.trimIndent().lines()
//        val result = wgEntryParser.parseWgDump(wgDumpOutput)
//        assertTrue(result.isSuccess)
//
//        val entries = result.getOrThrow()
//        assertEquals(3, entries.size)
//
//        // 验证有一个接口和两个peers
//        val interfaceCount = entries.count { it is WgEntry.Interface }
//        val peerCount = entries.count { it is WgEntry.Peer }
//        assertEquals(1, interfaceCount)
//        assertEquals(2, peerCount)
//    }
}
