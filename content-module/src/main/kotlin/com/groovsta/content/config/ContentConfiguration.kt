package com.groovsta.content.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EntityScan(basePackages = ["com.groovsta.content.model"])
@EnableJpaRepositories(basePackages = ["com.groovsta.content.repository"])
@EnableConfigurationProperties(ContentProperties::class)
class ContentConfiguration
