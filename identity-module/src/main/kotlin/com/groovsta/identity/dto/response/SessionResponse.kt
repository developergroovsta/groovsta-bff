package com.groovsta.identity.dto.response

import java.time.Instant

data class SessionResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresAt: Instant
)
