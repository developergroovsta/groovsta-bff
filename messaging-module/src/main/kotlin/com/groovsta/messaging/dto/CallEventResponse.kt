package com.groovsta.messaging.dto

import java.time.Instant
import java.util.UUID

data class CallEventResponse(
    val eventId: UUID = UUID.randomUUID(),
    val type: CallEventType,
    val call: CallResponse? = null,
    val signal: CallSignalResponse? = null,
    val occurredAt: Instant = Instant.now()
)
