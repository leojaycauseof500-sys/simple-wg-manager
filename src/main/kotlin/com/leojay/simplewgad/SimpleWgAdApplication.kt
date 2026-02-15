package com.leojay.simplewgad

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan("com.leojay.simplewgad.config.properties")
class SimpleWgAdApplication

fun main(args: Array<String>) {
    runApplication<SimpleWgAdApplication>(*args)
}
