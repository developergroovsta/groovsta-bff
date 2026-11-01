package com.groovsta.messaging.dto

data class ChatConversationPageResponse(
    val items: List<ChatConversationResponse>,
    val nextCursor: String?
)
