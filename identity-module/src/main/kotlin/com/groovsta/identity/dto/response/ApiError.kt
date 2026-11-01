package com.groovsta.identity.dto.response

import java.time.Instant

data class ApiError(
    val code: String,
    val message: String,
    val timestamp: Instant,
    val fields: Map<String, String> = emptyMap()
)
