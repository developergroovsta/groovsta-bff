package com.groovsta.notification.dto

import java.time.Instant
import java.util.UUID

data class NotificationRealtimeEventResponse(
    val eventId: UUID = UUID.randomUUID(),
    val type: NotificationRealtimeEventType,
    val notification: NotificationResponse? = null,
    val occurredAt: Instant = Instant.now()
)
