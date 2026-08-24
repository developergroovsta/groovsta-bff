package com.groovsta.notification.service

import com.groovsta.notification.dto.NotificationRealtimeEventResponse
import java.util.UUID

data class NotificationCommittedEvent(
    val recipientAccountId: UUID,
    val event: NotificationRealtimeEventResponse
)
