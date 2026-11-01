package com.groovsta.messaging.dto

data class ChatMessagePageResponse(
    val items: List<ChatMessageResponse>,
    val nextCursor: String?
)
