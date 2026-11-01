package com.groovsta.content.dto

import java.time.Instant
import java.util.UUID

data class PostMediaUploadResponse(
    val mediaId: UUID,
    val position: Int,
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: Instant
)
