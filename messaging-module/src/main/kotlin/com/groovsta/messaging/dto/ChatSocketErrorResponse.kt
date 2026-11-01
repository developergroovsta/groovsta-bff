package com.groovsta.messaging.dto

import java.time.Instant

data class ChatSocketErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = Instant.now()
)
