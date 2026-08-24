package com.groovsta.messaging.dto

data class IceServerResponse(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
)
