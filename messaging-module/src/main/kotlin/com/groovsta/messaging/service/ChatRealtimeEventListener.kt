package com.groovsta.messaging.service

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ChatRealtimeEventListener(
    private val messagingTemplate: SimpMessagingTemplate
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun chatEvent(event: ChatCommittedEvent) {
        event.participantAccountIds.forEach { accountId ->
            messagingTemplate.convertAndSendToUser(
                accountId.toString(),
                CHAT_EVENTS_DESTINATION,
                event.eventByAccountId[accountId] ?: event.event
            )
        }
    }

    private companion object {
        const val CHAT_EVENTS_DESTINATION = "/queue/chat-events"
    }
}
