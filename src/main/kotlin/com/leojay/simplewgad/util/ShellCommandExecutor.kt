package com.leojay.simplewgad.util

import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

//todo 考虑多平台
@Component
class ShellCommandExecutor : CommandExecutor() {
    override fun runCommand(
        command: String,
        timeoutSec: Long
    ): CommandResult {
        return try {
            // 自动为需要特权的命令添加 sudo
            val finalCommand = if (needsSudo(command)) {
                "sudo $command"
            } else {
                command
            }

            val process = createProcessBuilder(finalCommand).start()

            // 关键：设置超时防止挂起
            val completed = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return CommandResult(-1, "", "Command timeout after ${timeoutSec}s")
            }

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.exitValue()
            CommandResult(process.exitValue(), output, if (exitCode == 0) null else output)
        } catch (e: Exception) {
            logger.error("Command execution failed: {}", e.message)
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

    // 提取的方法，便于测试中重写
    fun createProcessBuilder(command: String): ProcessBuilder {
        return ProcessBuilder("/bin/bash", "-c", command)
            .redirectErrorStream(true)
    }

    /**
     * 判断命令是否需要 sudo 权限
     * 根据命令内容决定是否自动添加 sudo
     */
    private fun needsSudo(command: String): Boolean {
        // 如果命令已经以 sudo 开头，不再重复添加
        if (command.trimStart().startsWith("sudo ")) {
            return false
        }

        // 需要特权的命令列表
        val privilegedCommands = listOf(
            "wg ",
            "wg-quick ",
            "ip link",
            "ip addr",
            "ip route",
            "lsmod",
            "modprobe",
            "systemctl"
        )

        return privilegedCommands.any { command.startsWith(it) }
    }
}
