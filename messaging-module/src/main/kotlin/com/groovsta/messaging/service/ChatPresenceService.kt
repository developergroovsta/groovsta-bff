package com.groovsta.messaging.service

import com.groovsta.messaging.dto.ChatEventResponse
import com.groovsta.messaging.dto.ChatEventType
import com.groovsta.messaging.dto.ChatPresenceResponse
import com.groovsta.messaging.repository.ChatConversationRepository
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class ChatPresenceService(
    private val conversationRepository: ChatConversationRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {
    private val sessionsByAccount = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun isOnline(accountId: UUID): Boolean = sessionsByAccount[accountId]?.isNotEmpty() == true

    @EventListener
    fun connected(event: SessionConnectedEvent) {
        val accountId = event.user?.name?.let(UUID::fromString) ?: return
        val sessionId = SimpMessageHeaderAccessor.getSessionId(event.message.headers) ?: return
        val sessions = sessionsByAccount.computeIfAbsent(accountId) { ConcurrentHashMap.newKeySet() }
        val becameOnline = synchronized(sessions) {
            val wasOffline = sessions.isEmpty()
            sessions.add(sessionId)
            wasOffline
        }
        if (becameOnline) publish(accountId, true)
    }

    @EventListener
    fun disconnected(event: SessionDisconnectEvent) {
        val accountId = event.user?.name?.let(UUID::fromString) ?: return
        val sessions = sessionsByAccount[accountId] ?: return
        val becameOffline = synchronized(sessions) {
            sessions.remove(event.sessionId)
            sessions.isEmpty()
        }
        if (becameOffline) {
            sessionsByAccount.remove(accountId, sessions)
            publish(accountId, false)
        }
    }

    private fun publish(accountId: UUID, online: Boolean) {
        val event = ChatEventResponse(
            type = ChatEventType.PRESENCE_CHANGED,
            presence = ChatPresenceResponse(accountId, online, Instant.now())
        )
        conversationRepository.findConversationPartnerIds(accountId).forEach { partnerId ->
            messagingTemplate.convertAndSendToUser(partnerId.toString(), CHAT_EVENTS_DESTINATION, event)
        }
    }

    private companion object {
        const val CHAT_EVENTS_DESTINATION = "/queue/chat-events"
    }
}
