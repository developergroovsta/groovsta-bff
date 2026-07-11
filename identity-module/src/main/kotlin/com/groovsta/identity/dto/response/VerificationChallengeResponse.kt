package com.groovsta.identity.dto.response

import com.groovsta.identity.model.VerificationChannel
import java.time.Instant

data class VerificationChallengeResponse(
    val channel: VerificationChannel,
    val destination: String,
    val expiresAt: Instant
)
