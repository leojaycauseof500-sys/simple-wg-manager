package com.leojay.simplewgad.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import java.util.Locale


@Configuration
class I18NConfig : WebMvcConfigurer {

    @Bean
    fun localeResolver(): LocaleResolver {
        val resolver = SessionLocaleResolver()
        resolver.setDefaultLocale(Locale.ENGLISH)
        return resolver
    }

    @Bean
    fun localeChangeInterceptor(): LocaleChangeInterceptor {
        val interceptor = LocaleChangeInterceptor()
        interceptor.paramName = "lang"
        return interceptor
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(localeChangeInterceptor())
    }
}
