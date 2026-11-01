package com.groovsta.content.dto

import java.time.Instant

data class StoryUploadResponse(
    val objectKey: String,
    val uploadUrl: String,
    val uploadExpiresAt: Instant
)
