package com.groovsta.messaging.service.impl

import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.service.ProfileService
import com.groovsta.messaging.dto.ChatConversationPageResponse
import com.groovsta.messaging.dto.ChatConversationResponse
import com.groovsta.messaging.dto.ChatDeliveryReceiptResponse
import com.groovsta.messaging.dto.ChatEventResponse
import com.groovsta.messaging.dto.ChatEventType
import com.groovsta.messaging.dto.ChatMessagePageResponse
import com.groovsta.messaging.dto.ChatMessageResponse
import com.groovsta.messaging.dto.ChatPresenceResponse
import com.groovsta.messaging.dto.ChatReadReceiptResponse
import com.groovsta.messaging.dto.EditChatMessageRequest
import com.groovsta.messaging.dto.MarkConversationDeliveredRequest
import com.groovsta.messaging.dto.MarkConversationReadRequest
import com.groovsta.messaging.dto.ReactToChatMessageRequest
import com.groovsta.messaging.dto.SendChatMessageRequest
import com.groovsta.messaging.model.ChatConversation
import com.groovsta.messaging.model.ChatConversationMember
import com.groovsta.messaging.model.ChatMessage
import com.groovsta.messaging.model.ChatMessageReaction
import com.groovsta.messaging.model.ChatMessageType
import com.groovsta.messaging.repository.ChatConversationMemberRepository
import com.groovsta.messaging.repository.ChatConversationRepository
import com.groovsta.messaging.repository.ChatMessageRepository
import com.groovsta.messaging.repository.ChatMessageReactionRepository
import com.groovsta.messaging.service.ChatContentPreviewService
import com.groovsta.messaging.service.ChatCommittedEvent
import com.groovsta.messaging.service.ChatAccessService
import com.groovsta.messaging.service.ChatMessageEventPublisher
import com.groovsta.messaging.service.ChatPresenceService
import com.groovsta.messaging.service.ChatResponseAssembler
import com.groovsta.messaging.service.ChatService
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.Duration
import java.util.Base64
import java.util.UUID

@Service
class ChatServiceImpl(
    private val conversationRepository: ChatConversationRepository,
    private val memberRepository: ChatConversationMemberRepository,
    private val messageRepository: ChatMessageRepository,
    private val reactionRepository: ChatMessageReactionRepository,
    private val profileService: ProfileService,
    private val contentPreviewService: ChatContentPreviewService,
    private val responseAssembler: ChatResponseAssembler,
    private val accessService: ChatAccessService,
    private val messageEventPublisher: ChatMessageEventPublisher,
    private val presenceService: ChatPresenceService,
    private val eventPublisher: ApplicationEventPublisher,
    private val notificationPublisher: NotificationPublisher
) : ChatService {

    @Transactional(readOnly = true)
    override fun conversations(accountId: UUID, cursor: String?, limit: Int): ChatConversationPageResponse {
        accessService.requireActiveAccount(accountId)
        val pageSize = limit.coerceIn(1, 50)
        val decoded = cursor?.let(InboxCursor::decode)
        val fetched = if (decoded == null) {
            conversationRepository.findInbox(accountId, PageRequest.of(0, pageSize + 1))
        } else {
            conversationRepository.findInboxAfter(
                accountId,
                decoded.updatedAt,
                decoded.id,
                PageRequest.of(0, pageSize + 1)
            )
        }
        val page = fetched.take(pageSize)
        if (page.isEmpty()) return ChatConversationPageResponse(emptyList(), null)

        val conversationById = page.associateBy(ChatConversation::id)
        val messages = messageRepository.findAllById(page.mapNotNull(ChatConversation::lastMessageId))
        val messagesById = messages.associateBy(ChatMessage::id)
        val members = memberRepository.findByConversationIdIn(conversationById.keys)
        val messageResponses = responseAssembler.messages(accountId, messages, conversationById, members)
        val otherProfiles = profileService.summaries(
            page.mapTo(mutableSetOf()) { it.otherParticipant(accountId) }
        )

        val items = page.mapNotNull { conversation ->
            val lastMessage = conversation.lastMessageId
                ?.let(messagesById::get)
                ?.let { messageResponses[it.id] }
                ?: return@mapNotNull null
            ChatConversationResponse(
                id = conversation.id,
                otherUser = requireNotNull(otherProfiles[conversation.otherParticipant(accountId)]) {
                    "Conversation participant profile was not found"
                },
                otherUserOnline = presenceService.isOnline(conversation.otherParticipant(accountId)),
                lastMessage = lastMessage,
                hasUnread = lastMessage.sender.accountId != accountId && !lastMessage.isRead,
                updatedAt = conversation.updatedAt
            )
        }
        return ChatConversationPageResponse(
            items = items,
            nextCursor = page.lastOrNull()
                ?.takeIf { fetched.size > pageSize }
                ?.let(InboxCursor::from)
                ?.encode()
        )
    }

    @Transactional(readOnly = true)
    override fun messages(
        accountId: UUID,
        conversationId: UUID,
        cursor: String?,
        limit: Int
    ): ChatMessagePageResponse {
        accessService.requireActiveAccount(accountId)
        val conversation = accessService.participantConversation(conversationId, accountId)
        val pageSize = limit.coerceIn(1, 50)
        val decoded = cursor?.let(MessageCursor::decode)
        val fetched = if (decoded == null) {
            messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId,
                PageRequest.of(0, pageSize + 1)
            )
        } else {
            messageRepository.findPageAfter(
                conversationId,
                decoded.createdAt,
                decoded.id,
                PageRequest.of(0, pageSize + 1)
            )
        }
        val page = fetched.take(pageSize)
        val responses = responseAssembler.messages(
            accountId,
            page,
            mapOf(conversation.id to conversation),
            memberRepository.findByConversationIdIn(setOf(conversation.id))
        )
        return ChatMessagePageResponse(
            items = page.mapNotNull { responses[it.id] },
            nextCursor = page.lastOrNull()
                ?.takeIf { fetched.size > pageSize }
                ?.let(MessageCursor::from)
                ?.encode()
        )
    }

    @Transactional
    override fun send(accountId: UUID, request: SendChatMessageRequest): ChatMessageResponse {
        val existing = messageRepository.findBySenderAccountIdAndClientMessageId(
            accountId,
            request.clientMessageId
        )
        if (existing != null) return existingMessage(accountId, request, existing)

        accessService.requireCanMessage(accountId, request.recipientAccountId)
        if (request.type == ChatMessageType.CONTENT_SHARE) {
            contentPreviewService.requireShareableToBoth(
                requireNotNull(request.sharedContentId),
                accountId,
                request.recipientAccountId
            )
        }

        val conversation = accessService.directConversation(accountId, request.recipientAccountId)
        accessService.validateReply(conversation.id, request.replyToMessageId)
        val message = messageRepository.saveAndFlush(
            ChatMessage(
                conversationId = conversation.id,
                senderAccountId = accountId,
                clientMessageId = request.clientMessageId,
                type = request.type,
                text = request.text?.trim()?.takeIf(String::isNotEmpty),
                sharedContentId = request.sharedContentId,
                replyToMessageId = request.replyToMessageId
            )
        )
        conversationRepository.advanceLastMessage(conversation.id, message.id, message.createdAt)

        memberRepository.advanceReadPosition(
            conversationId = conversation.id,
            accountId = accountId,
            messageId = message.id,
            messageCreatedAt = message.createdAt,
            updatedAt = Instant.now()
        )

        notificationPublisher.publish(
            NotificationCommand(
                request.recipientAccountId,
                accountId,
                NotificationType.DIRECT_MESSAGE,
                NotificationTargetType.CONVERSATION,
                conversation.id,
                message.id,
                message.text ?: "Shared a post or reel"
            )
        )

        return changedMessage(accountId, message, conversation, ChatEventType.MESSAGE_CREATED)
    }

    @Transactional
    override fun edit(
        accountId: UUID,
        messageId: UUID,
        request: EditChatMessageRequest
    ): ChatMessageResponse {
        val (message, conversation) = authoredMessage(accountId, messageId)
        if (message.unsentAt != null) throw IdentityConflictException("An unsent message cannot be edited")
        if (Instant.now().isAfter(message.createdAt.plus(EDIT_WINDOW))) {
            throw IdentityConflictException("Messages can only be edited within 10 seconds")
        }
        val text = request.text?.trim()?.takeIf(String::isNotEmpty)
        if (text != null && text.length > 2000) throw IllegalArgumentException("Message text is too long")
        if (message.type == ChatMessageType.TEXT && text == null) {
            throw IllegalArgumentException("Text messages cannot be blank")
        }
        message.text = text
        message.editedAt = Instant.now()
        val saved = messageRepository.saveAndFlush(message)
        return changedMessage(accountId, saved, conversation, ChatEventType.MESSAGE_UPDATED)
    }

    @Transactional
    override fun unsend(accountId: UUID, messageId: UUID): ChatMessageResponse {
        val (message, conversation) = authoredMessage(accountId, messageId)
        if (message.unsentAt == null) {
            message.unsentAt = Instant.now()
            messageRepository.saveAndFlush(message)
        }
        return changedMessage(accountId, message, conversation, ChatEventType.MESSAGE_UNSENT)
    }

    @Transactional
    override fun react(
        accountId: UUID,
        messageId: UUID,
        request: ReactToChatMessageRequest
    ): ChatMessageResponse {
        val (message, conversation) = participantMessage(accountId, messageId)
        if (message.unsentAt != null) throw IdentityConflictException("An unsent message cannot be reacted to")
        val emoji = request.emoji.trim()
        if (emoji.isEmpty() || emoji.length > 32) throw IllegalArgumentException("Reaction must be 1 to 32 characters")
        val reaction = reactionRepository.findByMessageIdAndAccountId(message.id, accountId)
            ?.also { it.emoji = emoji }
            ?: ChatMessageReaction(messageId = message.id, accountId = accountId, emoji = emoji)
        reactionRepository.saveAndFlush(reaction)
        return changedMessage(accountId, message, conversation, ChatEventType.MESSAGE_REACTION_UPDATED)
    }

    @Transactional
    override fun removeReaction(accountId: UUID, messageId: UUID): ChatMessageResponse {
        val (message, conversation) = participantMessage(accountId, messageId)
        reactionRepository.findByMessageIdAndAccountId(message.id, accountId)?.let {
            reactionRepository.delete(it)
            reactionRepository.flush()
        }
        return changedMessage(accountId, message, conversation, ChatEventType.MESSAGE_REACTION_UPDATED)
    }

    @Transactional
    override fun markDelivered(
        accountId: UUID,
        conversationId: UUID,
        request: MarkConversationDeliveredRequest
    ): ChatDeliveryReceiptResponse {
        accessService.requireActiveAccount(accountId)
        val conversation = accessService.participantConversation(conversationId, accountId)
        val message = messageRepository.findById(request.messageId).orElseThrow { messageNotFound() }
        if (message.conversationId != conversation.id || message.senderAccountId == accountId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only received messages can be marked delivered")
        }
        val deliveredAt = Instant.now()
        messageRepository.markIncomingDeliveredThrough(
            conversation.id,
            accountId,
            message.createdAt,
            deliveredAt
        )
        val receipt = ChatDeliveryReceiptResponse(conversation.id, accountId, message.id, deliveredAt)
        eventPublisher.publishEvent(
            ChatCommittedEvent(
                accessService.participants(conversation),
                ChatEventResponse(ChatEventType.MESSAGE_DELIVERED, deliveryReceipt = receipt)
            )
        )
        return receipt
    }

    @Transactional
    override fun markRead(
        accountId: UUID,
        conversationId: UUID,
        request: MarkConversationReadRequest
    ): ChatReadReceiptResponse {
        accessService.requireActiveAccount(accountId)
        val conversation = accessService.participantConversation(conversationId, accountId)
        val message = messageRepository.findById(request.messageId).orElseThrow { messageNotFound() }
        if (message.conversationId != conversation.id) messageNotFound()
        if (message.senderAccountId != accountId) {
            messageRepository.markIncomingDeliveredThrough(
                conversation.id,
                accountId,
                message.createdAt,
                Instant.now()
            )
        }

        val member = memberRepository.findByConversationIdAndAccountId(conversation.id, accountId)
            ?: throw IllegalStateException("Conversation member was not found")
        val changed = memberRepository.advanceReadPosition(
            conversationId = conversation.id,
            accountId = accountId,
            messageId = message.id,
            messageCreatedAt = message.createdAt,
            updatedAt = Instant.now()
        ) > 0
        val currentMember = if (changed) {
            memberRepository.findByConversationIdAndAccountId(conversation.id, accountId)
                ?: throw IllegalStateException("Conversation member was not found")
        } else {
            member
        }

        val receipt = ChatReadReceiptResponse(
            conversationId = conversation.id,
            readerAccountId = accountId,
            messageId = currentMember.lastReadMessageId ?: message.id,
            readAt = Instant.now()
        )
        if (changed) {
            eventPublisher.publishEvent(
                ChatCommittedEvent(
                    participantAccountIds = accessService.participants(conversation),
                    event = ChatEventResponse(ChatEventType.MESSAGE_READ, readReceipt = receipt)
                )
            )
        }
        return receipt
    }

    @Transactional(readOnly = true)
    override fun presence(accountId: UUID, targetAccountId: UUID): ChatPresenceResponse {
        accessService.requireActiveAccount(accountId)
        if (accountId != targetAccountId) {
            accessService.requireAccount(targetAccountId)
        }
        return ChatPresenceResponse(targetAccountId, presenceService.isOnline(targetAccountId), Instant.now())
    }

    private fun existingMessage(
        accountId: UUID,
        request: SendChatMessageRequest,
        existing: ChatMessage
    ): ChatMessageResponse {
        val conversation = accessService.participantConversation(existing.conversationId, accountId)
        val sameRequest = conversation.otherParticipant(accountId) == request.recipientAccountId &&
            existing.type == request.type &&
            existing.text == request.text?.trim()?.takeIf(String::isNotEmpty) &&
            existing.sharedContentId == request.sharedContentId &&
            existing.replyToMessageId == request.replyToMessageId
        if (!sameRequest) {
            throw IdentityConflictException("clientMessageId was already used for a different message")
        }
        return response(
            accountId,
            existing,
            conversation,
            memberRepository.findByConversationIdIn(setOf(conversation.id))
        )
    }

    private fun response(
        accountId: UUID,
        message: ChatMessage,
        conversation: ChatConversation,
        members: Collection<ChatConversationMember>
    ): ChatMessageResponse = requireNotNull(
        responseAssembler.messages(
            accountId,
            listOf(message),
            mapOf(conversation.id to conversation),
            members
        )[message.id]
    )

    private fun changedMessage(
        accountId: UUID,
        message: ChatMessage,
        conversation: ChatConversation,
        eventType: ChatEventType
    ): ChatMessageResponse {
        return messageEventPublisher.changed(accountId, message, conversation, eventType)
    }

    private fun participantMessage(accountId: UUID, messageId: UUID): Pair<ChatMessage, ChatConversation> {
        accessService.requireActiveAccount(accountId)
        val message = messageRepository.findById(messageId).orElseThrow { messageNotFound() }
        val conversation = accessService.participantConversation(message.conversationId, accountId)
        return message to conversation
    }

    private fun authoredMessage(accountId: UUID, messageId: UUID): Pair<ChatMessage, ChatConversation> {
        val pair = participantMessage(accountId, messageId)
        if (pair.first.senderAccountId != accountId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the sender can modify this message")
        }
        return pair
    }

    private fun messageNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Message was not found")

    private data class InboxCursor(val updatedAt: Instant, val id: UUID) {
        fun encode(): String = encodeCursor(updatedAt, id)

        companion object {
            fun from(conversation: ChatConversation) = InboxCursor(conversation.updatedAt, conversation.id)
            fun decode(value: String): InboxCursor = decodeCursor(value, "inbox")
                .let { InboxCursor(it.first, it.second) }
        }
    }

    private data class MessageCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = encodeCursor(createdAt, id)

        companion object {
            fun from(message: ChatMessage) = MessageCursor(message.createdAt, message.id)
            fun decode(value: String): MessageCursor = decodeCursor(value, "message")
                .let { MessageCursor(it.first, it.second) }
        }
    }

    private companion object {
        val EDIT_WINDOW: Duration = Duration.ofSeconds(10)

        fun encodeCursor(instant: Instant, id: UUID): String =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString("$instant|$id".toByteArray(Charsets.UTF_8))

        fun decodeCursor(value: String, name: String): Pair<Instant, UUID> {
            val decoded = runCatching {
                String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
            }.getOrElse { throw IllegalArgumentException("Invalid chat $name cursor") }
            val parts = decoded.split('|', limit = 2)
            require(parts.size == 2) { "Invalid chat $name cursor" }
            return runCatching { Instant.parse(parts[0]) to UUID.fromString(parts[1]) }
                .getOrElse { throw IllegalArgumentException("Invalid chat $name cursor") }
        }
    }
}
