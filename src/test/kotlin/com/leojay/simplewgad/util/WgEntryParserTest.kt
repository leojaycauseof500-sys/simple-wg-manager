package com.leojay.simplewgad.util

import com.leojay.simplewgad.model.WgEntry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName

class WgEntryParserTest {

    private val parser = WgEntryParser()

    @Test
    @DisplayName("应正确解析Interface行")
    fun `should parse interface line correctly`() {
        val input = listOf(
            "wg0\tABCDEF123456\tPUBKEY123\t51820\t0"
        )

        val result = parser.parseWgDump(input)

        assertEquals(1, result.size)
        assertTrue(result[0] is WgEntry.Interface)

        val interfaceEntry = result[0] as WgEntry.Interface
        assertEquals("wg0", interfaceEntry.name)
        assertEquals("PUBKEY123", interfaceEntry.publicKey)
        assertEquals(51820, interfaceEntry.listenPort)
    }

    @Test
    @DisplayName("应正确解析Peer行")
    fun `should parse peer line correctly`() {
        val input = listOf(
            "wg0\tPEERKEY123\t(none)\t192.168.1.100:51820\t10.0.0.2/32\t1234567890\t1024\t2048\t0"
        )

        val result = parser.parseWgDump(input)

        assertEquals(1, result.size)
        assertTrue(result[0] is WgEntry.Peer)

        val peerEntry = result[0] as WgEntry.Peer
        assertEquals("wg0", peerEntry.interfaceName)
        assertEquals("PEERKEY123", peerEntry.publicKey)
        assertEquals("192.168.1.100:51820", peerEntry.endpoint)
        assertEquals(listOf("10.0.0.2/32"), peerEntry.allowedIps)
        assertEquals(1234567890L, peerEntry.latestHandshake)
        assertEquals(1024L, peerEntry.transferRx)
        assertEquals(2048L, peerEntry.transferTx)
    }

    @Test
    @DisplayName("应正确处理空endpoint")
    fun `should handle empty endpoint correctly`() {
        val input = listOf(
            "wg0\tPEERKEY123\t(none)\t(none)\t10.0.0.2/32\t0\t0\t0\t0"
        )

        val result = parser.parseWgDump(input)

        assertEquals(1, result.size)
        val peerEntry = result[0] as WgEntry.Peer
        assertEquals("", peerEntry.endpoint) // (none) 应该转换为空字符串
    }

    @Test
    @DisplayName("应解析多个allowed IPs")
    fun `should parse multiple allowed IPs`() {
        val input = listOf(
            "wg0\tPEERKEY123\t(none)\t192.168.1.100:51820\t10.0.0.2/32,10.0.0.3/32\t1234567890\t1024\t2048\t0"
        )

        val result = parser.parseWgDump(input)

        val peerEntry = result[0] as WgEntry.Peer
        assertEquals(2, peerEntry.allowedIps.size)
        assertEquals("10.0.0.2/32", peerEntry.allowedIps[0])
        assertEquals("10.0.0.3/32", peerEntry.allowedIps[1])
    }

    @Test
    @DisplayName("应跳过格式不正确的行")
    fun `should skip malformed lines`() {
        val input = listOf(
            "wg0\tABCDEF123456\tPUBKEY123\t51820\t0", // 正确
            "malformed line", // 格式错误
            "wg0\tPEERKEY123\t(none)\t192.168.1.100:51820\t10.0.0.2/32\t1234567890\t1024\t2048\t0" // 正确
        )

        val result = parser.parseWgDump(input)

        assertEquals(2, result.size) // 应该只有两行被正确解析
    }

    @Test
    @DisplayName("应处理空输入")
    fun `should handle empty input`() {
        val result = parser.parseWgDump(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("应处理无效端口号")
    fun `should handle invalid port number`() {
        val input = listOf(
            "wg0\tABCDEF123456\tPUBKEY123\tinvalid\t0"
        )

        val result = parser.parseWgDump(input)

        val interfaceEntry = result[0] as WgEntry.Interface
        assertEquals(0, interfaceEntry.listenPort) // 无效端口应该返回0
    }

    @Test
    @DisplayName("应处理无效数字字段")
    fun `should handle invalid numeric fields`() {
        val input = listOf(
            "wg0\tPEERKEY123\t(none)\t192.168.1.100:51820\t10.0.0.2/32\tinvalid\tinvalid\tinvalid\t0"
        )

        val result = parser.parseWgDump(input)

        val peerEntry = result[0] as WgEntry.Peer
        assertEquals(0L, peerEntry.latestHandshake)
        assertEquals(0L, peerEntry.transferRx)
        assertEquals(0L, peerEntry.transferTx)
    }

    @Test
    @DisplayName("应解析完整的wg dump输出")
    fun `should parse complete wg dump output`() {
        val input = listOf(
            "wg0\tABCDEF123456\tPUBKEY123\t51820\t0",
            "wg0\tPEERKEY123\t(none)\t192.168.1.100:51820\t10.0.0.2/32\t1234567890\t1024\t2048\t0",
            "wg0\tPEERKEY456\t(none)\t192.168.1.101:51820\t10.0.0.3/32\t1234567891\t2048\t4096\t0"
        )

        val result = parser.parseWgDump(input)

        assertEquals(3, result.size)

        // 验证第一个是Interface
        assertTrue(result[0] is WgEntry.Interface)

        // 验证后面两个是Peer
        assertTrue(result[1] is WgEntry.Peer)
        assertTrue(result[2] is WgEntry.Peer)

        val peer1 = result[1] as WgEntry.Peer
        val peer2 = result[2] as WgEntry.Peer

        assertEquals("PEERKEY123", peer1.publicKey)
        assertEquals("PEERKEY456", peer2.publicKey)
    }
}
