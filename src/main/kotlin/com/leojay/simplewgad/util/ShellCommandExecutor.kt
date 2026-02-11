package com.leojay.simplewgad.util

import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class ShellCommandExecutor : CommandExecutor() {
    override fun runCommand(
        command: String,
        timeoutSec: Long
    ): CommandResult {
        return try {
            val process = ProcessBuilder("/bin/bash", "-c", command)
                .redirectErrorStream(true)
                .start()

            // 关键：设置超时防止挂起
            val completed = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return CommandResult(-1, "", "Command timeout after ${timeoutSec}s")
            }

            val output = process.inputStream.bufferedReader().readText()
            CommandResult(process.exitValue(), output, "")
        } catch (e: Exception) {
            logger.error("Command execution failed: {}", e.message)
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

}
}
