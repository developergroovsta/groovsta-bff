package com.groovsta.messaging.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("groovsta.calls")
data class CallProperties(
    var maxParticipants: Int = 8,
    var ringTimeout: Duration = Duration.ofSeconds(30),
    var heartbeatTimeout: Duration = Duration.ofSeconds(90),
    var cleanupInterval: Duration = Duration.ofSeconds(10),
    var stunUrls: List<String> = listOf("stun:stun.l.google.com:19302"),
    var turnUrls: List<String> = emptyList(),
    var turnUsername: String? = null,
    var turnCredential: String? = null
)
