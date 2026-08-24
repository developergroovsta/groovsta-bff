package com.groovsta.messaging.repository

import com.groovsta.messaging.model.ChatConversation
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChatConversationRepository : JpaRepository<ChatConversation, UUID> {

    fun findByParticipantOneAccountIdAndParticipantTwoAccountId(
        participantOneAccountId: UUID,
        participantTwoAccountId: UUID
    ): ChatConversation?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO chat_conversations (
            id,
            participant_one_account_id,
            participant_two_account_id,
            created_at,
            updated_at
        )
        VALUES (:id, :participantOne, :participantTwo, :createdAt, :updatedAt)
        ON CONFLICT (participant_one_account_id, participant_two_account_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("participantOne") participantOne: UUID,
        @Param("participantTwo") participantTwo: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("updatedAt") updatedAt: Instant
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE chat_conversations
        SET last_message_id = :messageId,
            updated_at = :messageCreatedAt
        WHERE id = :conversationId
          AND (
              last_message_id IS NULL
              OR updated_at < :messageCreatedAt
              OR (updated_at = :messageCreatedAt AND last_message_id < :messageId)
          )
        """,
        nativeQuery = true
    )
    fun advanceLastMessage(
        @Param("conversationId") conversationId: UUID,
        @Param("messageId") messageId: UUID,
        @Param("messageCreatedAt") messageCreatedAt: Instant
    ): Int

    @Query(
        """
        select conversation from ChatConversation conversation
        where (conversation.participantOneAccountId = :accountId
            or conversation.participantTwoAccountId = :accountId)
          and conversation.lastMessageId is not null
        order by conversation.updatedAt desc, conversation.id desc
        """
    )
    fun findInbox(
        @Param("accountId") accountId: UUID,
        pageable: Pageable
    ): List<ChatConversation>

    @Query(
        """
        select conversation from ChatConversation conversation
        where (conversation.participantOneAccountId = :accountId
            or conversation.participantTwoAccountId = :accountId)
          and conversation.lastMessageId is not null
          and (
              conversation.updatedAt < :updatedAt
              or (conversation.updatedAt = :updatedAt and conversation.id < :id)
          )
        order by conversation.updatedAt desc, conversation.id desc
        """
    )
    fun findInboxAfter(
        @Param("accountId") accountId: UUID,
        @Param("updatedAt") updatedAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ChatConversation>

    @Query(
        """
        select case
            when conversation.participantOneAccountId = :accountId
                then conversation.participantTwoAccountId
            else conversation.participantOneAccountId
        end
        from ChatConversation conversation
        where conversation.participantOneAccountId = :accountId
           or conversation.participantTwoAccountId = :accountId
        """
    )
    fun findConversationPartnerIds(@Param("accountId") accountId: UUID): Set<UUID>
}
