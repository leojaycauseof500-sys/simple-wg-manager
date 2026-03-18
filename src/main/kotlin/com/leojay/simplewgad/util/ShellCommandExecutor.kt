package com.leojay.simplewgad.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

//todo 考虑多平台
@Component
class ShellCommandExecutor(
    @Value("\${wg-manager.default.bash-path}") private val bashPath: String,
) : CommandExecutor() {
    override fun runCommand(
        command: String,
        timeoutSec: Long
    ): CommandResult {
        return try {
            // 自动为需要特权的命令添加 sudo, 和为特殊命名进行预处理
            val finalCommand = preProcessing(command).let { cmd ->
                if (needsSudo(cmd)) "sudo $cmd" else cmd
            }

            val process = createProcessBuilder(finalCommand).start()

            // 设置超时防止挂起
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
        return ProcessBuilder(bashPath.ifEmpty({ "/bin/bash" }), "-c", command)
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
            "systemctl",
            "$bashPath wg-quick"
        )

        return privilegedCommands.any { command.startsWith(it) }
    }

    /**
     * 部分命令需要特殊处理
     */
    private fun preProcessing(command: String): String {
        //wg-quick可能需要bash版本4.0以上，且默认使用/bin/bash。所以需要手动指定一下bash
//        return if (command.contains("wg-quick")) {
//            "$bashPath $command"
//        } else {
//            command
//        }
        return command
    }
}
