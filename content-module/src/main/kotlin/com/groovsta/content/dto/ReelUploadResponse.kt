package com.groovsta.content.dto

import java.time.Instant
import java.util.UUID

data class ReelUploadResponse(
    val reelId: UUID,
    val objectKey: String,
    val uploadUrl: String,
    val expiresAt: Instant
)
