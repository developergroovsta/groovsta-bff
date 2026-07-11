package com.groovsta.identity.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EntityScan(basePackages = ["com.groovsta.identity.model"])
@EnableJpaRepositories(basePackages = ["com.groovsta.identity.repository"])
class IdentityConfiguration {
    @Bean
    fun identityPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
