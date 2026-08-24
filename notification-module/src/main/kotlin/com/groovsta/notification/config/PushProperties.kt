package com.groovsta.notification.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("groovsta.push")
data class PushProperties(
    var enabled: Boolean = false,
    var pollInterval: Long = 2_000,
    var batchSize: Int = 100,
    var leaseDuration: Duration = Duration.ofSeconds(30),
    var maxAttempts: Int = 6,
    var initialRetryDelay: Duration = Duration.ofSeconds(5),
    var maxRetryDelay: Duration = Duration.ofMinutes(10),
    var notificationTtl: Duration = Duration.ofDays(7),
    var includeSensitivePreviews: Boolean = false
)
