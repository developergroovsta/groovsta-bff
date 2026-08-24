package com.groovsta.notification.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EntityScan(basePackages = ["com.groovsta.notification.model"])
@EnableJpaRepositories(basePackages = ["com.groovsta.notification.repository"])
@EnableConfigurationProperties(PushProperties::class)
@EnableScheduling
class NotificationConfiguration
