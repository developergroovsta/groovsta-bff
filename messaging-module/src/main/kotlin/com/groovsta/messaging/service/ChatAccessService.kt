package com.groovsta.messaging.service

import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.SocialGraphService
import com.groovsta.messaging.model.ChatConversation
import com.groovsta.messaging.repository.ChatConversationMemberRepository
import com.groovsta.messaging.repository.ChatConversationRepository
import com.groovsta.messaging.repository.ChatMessageRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Component
class ChatAccessService(
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val socialGraphService: SocialGraphService,
    private val conversationRepository: ChatConversationRepository,
    private val memberRepository: ChatConversationMemberRepository,
    private val messageRepository: ChatMessageRepository
) {
    fun requireActiveAccount(accountId: UUID) {
        val account = accountRepository.findById(accountId).orElseThrow { profileNotFound() }
        if (account.status != AccountStatus.ACTIVE) {
            throw IdentityConflictException("Account must be active to use chat")
        }
    }

    fun requireAccount(accountId: UUID) {
        accountRepository.findById(accountId).orElseThrow { profileNotFound() }
    }

    fun requireCanMessage(senderAccountId: UUID, recipientAccountId: UUID) {
        if (senderAccountId == recipientAccountId) {
            throw IdentityConflictException("An account cannot message itself")
        }
        requireActiveAccount(senderAccountId)
        val recipient = accountRepository.findById(recipientAccountId).orElseThrow { profileNotFound() }
        if (recipient.status != AccountStatus.ACTIVE) profileNotFound()
        val recipientProfile = profileRepository.findByAccountId(recipientAccountId).orElseThrow { profileNotFound() }
        if (!recipientProfile.isPublic && !socialGraphService.isFollowing(senderAccountId, recipientAccountId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Messaging this private account is not allowed")
        }
    }

    fun directConversation(firstAccountId: UUID, secondAccountId: UUID): ChatConversation {
        val (participantOne, participantTwo) = canonicalParticipants(firstAccountId, secondAccountId)
        val now = Instant.now()
        conversationRepository.insertIfAbsent(UUID.randomUUID(), participantOne, participantTwo, now, now)
        val conversation = conversationRepository.findByParticipantOneAccountIdAndParticipantTwoAccountId(
            participantOne,
            participantTwo
        ) ?: throw IllegalStateException("Conversation could not be created")
        memberRepository.insertIfAbsent(UUID.randomUUID(), conversation.id, participantOne, now, now)
        memberRepository.insertIfAbsent(UUID.randomUUID(), conversation.id, participantTwo, now, now)
        return conversation
    }

    fun participantConversation(conversationId: UUID, accountId: UUID): ChatConversation =
        conversationRepository.findById(conversationId)
            .orElseThrow { conversationNotFound() }
            .also { if (!it.contains(accountId)) conversationNotFound() }

    fun validateReply(conversationId: UUID, replyToMessageId: UUID?) {
        replyToMessageId?.let { replyId ->
            val reply = messageRepository.findById(replyId).orElseThrow { messageNotFound() }
            if (reply.conversationId != conversationId || reply.unsentAt != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "The replied-to message is unavailable")
            }
        }
    }

    fun participants(conversation: ChatConversation): Set<UUID> =
        setOf(conversation.participantOneAccountId, conversation.participantTwoAccountId)

    private fun canonicalParticipants(first: UUID, second: UUID): Pair<UUID, UUID> =
        if (first.toString() < second.toString()) first to second else second to first

    private fun conversationNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation was not found")

    private fun messageNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Message was not found")

    private fun profileNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Profile was not found")
}
