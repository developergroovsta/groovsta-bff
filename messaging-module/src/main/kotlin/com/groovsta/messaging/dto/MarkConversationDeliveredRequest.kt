package com.groovsta.messaging.dto

import java.util.UUID

data class MarkConversationDeliveredRequest(
    val messageId: UUID
)
