package com.groovsta.messaging.dto

import java.time.Instant
import java.util.UUID

data class ChatReadReceiptResponse(
    val conversationId: UUID,
    val readerAccountId: UUID,
    val messageId: UUID,
    val readAt: Instant
)
