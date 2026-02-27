package com.leojay.simplewgad.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class WgConfig {
    @Bean
    fun objectMapper() = ObjectMapper()

}
