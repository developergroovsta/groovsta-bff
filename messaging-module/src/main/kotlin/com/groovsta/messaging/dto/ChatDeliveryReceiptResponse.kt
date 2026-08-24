package com.groovsta.messaging.dto

import java.time.Instant
import java.util.UUID

data class ChatDeliveryReceiptResponse(
    val conversationId: UUID,
    val recipientAccountId: UUID,
    val messageId: UUID,
    val deliveredAt: Instant
)
