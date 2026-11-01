package com.groovsta.content.dto

import java.time.Instant

data class ContentMediaUploadResponse(
    val position: Int,
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: Instant
)
