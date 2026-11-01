package com.groovsta.identity.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.core.env.Environment

@Configuration
@EntityScan(basePackages = ["com.groovsta.identity.model"])
@EnableJpaRepositories(basePackages = ["com.groovsta.identity.repository"])
class IdentityConfiguration {
    @Bean
    fun identityPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun identityConfigLogger(
        properties: IdentityProperties,
        environment: Environment
    ): ApplicationRunner = ApplicationRunner {
        logger.info(
            "identity.config loaded googleAuthEnabled={} firebaseEnabled={} firebaseProjectIdPresent={} " +
                "firebaseCredentialsPathPresent={}",
            properties.google.enabled,
            environment.getProperty("groovsta.firebase.enabled", Boolean::class.java, false),
            !environment.getProperty("groovsta.firebase.project-id").isNullOrBlank(),
            !environment.getProperty("groovsta.firebase.credentials-path").isNullOrBlank()
        )
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(IdentityConfiguration::class.java)
    }
}
