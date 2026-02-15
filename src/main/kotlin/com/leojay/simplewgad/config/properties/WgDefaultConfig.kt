package com.leojay.simplewgad.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "wg-manager.default")
data class WgDefaultConfig(
    val interfaceName : String = "wg0"
)

