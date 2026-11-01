package com.groovsta.messaging.dto

import java.util.UUID

data class MarkConversationReadRequest(
    val messageId: UUID
)
