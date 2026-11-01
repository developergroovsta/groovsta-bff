package com.groovsta.messaging.service

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.slf4j.LoggerFactory

@Component
class CallRealtimeEventListener(
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun callEvent(event: CallCommittedEvent) {
        log.debug(
            "call.socket publish recipients={}",
            event.eventByAccountId.size
        )
        event.eventByAccountId.forEach { (accountId, payload) ->
            log.debug(
                "call.socket publish recipientAccountId={} eventType={} callId={}",
                accountId,
                payload.type,
                payload.call?.id
            )
            messagingTemplate.convertAndSendToUser(accountId.toString(), CALL_EVENTS_DESTINATION, payload)
        }
    }

    companion object {
        const val CALL_EVENTS_DESTINATION = "/queue/call-events"
    }
}
