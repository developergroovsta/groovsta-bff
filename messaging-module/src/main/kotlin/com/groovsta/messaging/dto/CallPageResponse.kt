package com.groovsta.messaging.dto

data class CallPageResponse(
    val items: List<CallResponse>,
    val nextCursor: String?
)
