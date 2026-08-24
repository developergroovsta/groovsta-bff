package com.groovsta.content.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("groovsta.content")
data class ContentProperties(
    var maxReelBytes: Long = 500L * 1024 * 1024,
    var maxPostImageBytes: Long = 20L * 1024 * 1024,
    var maxStoryImageBytes: Long = 20L * 1024 * 1024,
    var maxStoryVideoBytes: Long = 500L * 1024 * 1024,
    var transcodeCallbackSecret: String = "development-transcode-secret"
)
