package com.groovsta.messaging.dto

import java.time.Instant
import java.util.UUID

data class ChatPresenceResponse(
    val accountId: UUID,
    val online: Boolean,
    val changedAt: Instant
)
