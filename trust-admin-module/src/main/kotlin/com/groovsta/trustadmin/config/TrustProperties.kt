package com.groovsta.trustadmin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("groovsta.trust")
data class TrustProperties(var moderationAdminKey: String = "development-moderation-key")
