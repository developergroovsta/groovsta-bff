package com.groovsta.trustadmin.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EntityScan(basePackages = ["com.groovsta.trustadmin.model"])
@EnableJpaRepositories(basePackages = ["com.groovsta.trustadmin.repository"])
@EnableConfigurationProperties(TrustProperties::class)
class TrustConfiguration
