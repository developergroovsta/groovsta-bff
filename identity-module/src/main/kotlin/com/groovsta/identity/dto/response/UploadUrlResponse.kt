package com.groovsta.identity.dto.response

import java.time.Instant

data class UploadUrlResponse(
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: Instant
)
