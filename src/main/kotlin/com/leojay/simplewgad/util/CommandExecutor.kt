package com.leojay.simplewgad.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

abstract class CommandExecutor {
    protected val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    data class CommandResult(
        val exitCode : Int,
        val output: String,
        val errMsg: String?
    )

    abstract fun runCommand(command: String, timeoutSec: Long = 30): CommandResult
}
