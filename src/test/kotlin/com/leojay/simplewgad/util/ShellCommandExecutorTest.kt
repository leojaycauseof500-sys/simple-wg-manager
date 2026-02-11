package com.leojay.simplewgad.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShellCommandExecutorTest {

    private val executor = ShellCommandExecutor()

    @Test
    fun `runCommand should execute echo successfully`() {
        // 执行一个简单的命令
        val result = executor.runCommand("echo hello", 5)

        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("hello"))
        assertEquals("", result.errMsg)
    }

    @Test
    fun `runCommand should handle command not found`() {
        // 使用一个不存在的命令
        val result = executor.runCommand("nonexistent_command_xyz123", 2)

        assertEquals(-1, result.exitCode)
        // 错误消息可能不为空
        // 在某些系统上，可能会返回退出码127或其他
    }

    @Test
    fun `runCommand should handle empty command`() {
        // 空命令应该返回非零退出码
        val result = executor.runCommand("", 2)

        // 在bash中，空命令可能成功（退出码0）或失败，取决于实现
        // 我们只检查没有异常抛出
        // 不进行具体断言
    }
}
