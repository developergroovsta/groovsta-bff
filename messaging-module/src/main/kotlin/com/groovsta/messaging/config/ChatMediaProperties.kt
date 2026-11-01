package com.groovsta.messaging.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("groovsta.chat.media")
data class ChatMediaProperties(
    var maxAttachmentsPerMessage: Int = 10,
    var maxImageBytes: Long = 20L * 1024 * 1024,
    var maxVideoBytes: Long = 500L * 1024 * 1024,
    var maxBatchBytes: Long = 500L * 1024 * 1024,
    var maxDimensionPixels: Int = 12_000,
    var maxVideoDuration: Duration = Duration.ofMinutes(15),
    var abandonedUploadRetention: Duration = Duration.ofHours(24),
    var cleanupInterval: Long = 3_600_000
)
