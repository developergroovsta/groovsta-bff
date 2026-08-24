package com.groovsta.messaging.service

import com.groovsta.messaging.dto.ChatEventResponse
import com.groovsta.messaging.dto.ChatEventType
import com.groovsta.messaging.dto.ChatMessageResponse
import com.groovsta.messaging.model.ChatConversation
import com.groovsta.messaging.model.ChatMessage
import com.groovsta.messaging.repository.ChatConversationMemberRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChatMessageEventPublisher(
    private val memberRepository: ChatConversationMemberRepository,
    private val responseAssembler: ChatResponseAssembler,
    private val eventPublisher: ApplicationEventPublisher,
    private val accessService: ChatAccessService
) {
    fun response(
        viewerAccountId: UUID,
        message: ChatMessage,
        conversation: ChatConversation
    ): ChatMessageResponse = requireNotNull(
        responseAssembler.messages(
            viewerAccountId,
            listOf(message),
            mapOf(conversation.id to conversation),
            memberRepository.findByConversationIdIn(setOf(conversation.id))
        )[message.id]
    )

    fun changed(
        viewerAccountId: UUID,
        message: ChatMessage,
        conversation: ChatConversation,
        eventType: ChatEventType
    ): ChatMessageResponse {
        val participantIds = accessService.participants(conversation)
        val members = memberRepository.findByConversationIdIn(setOf(conversation.id))
        val responses = participantIds.associateWith { viewerId ->
            requireNotNull(
                responseAssembler.messages(
                    viewerId,
                    listOf(message),
                    mapOf(conversation.id to conversation),
                    members
                )[message.id]
            )
        }
        eventPublisher.publishEvent(
            ChatCommittedEvent(
                participantIds,
                ChatEventResponse(eventType, message = requireNotNull(responses[viewerAccountId])),
                responses.mapValues { ChatEventResponse(eventType, message = it.value) }
            )
        )
        return requireNotNull(responses[viewerAccountId])
    }
}
