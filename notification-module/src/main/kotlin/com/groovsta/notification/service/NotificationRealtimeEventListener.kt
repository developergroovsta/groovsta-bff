package com.groovsta.notification.service

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class NotificationRealtimeEventListener(
    private val messagingTemplate: SimpMessagingTemplate
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun notificationEvent(event: NotificationCommittedEvent) {
        messagingTemplate.convertAndSendToUser(
            event.recipientAccountId.toString(),
            NOTIFICATION_EVENTS_DESTINATION,
            event.event
        )
    }

    private companion object {
        const val NOTIFICATION_EVENTS_DESTINATION = "/queue/notification-events"
    }
}
