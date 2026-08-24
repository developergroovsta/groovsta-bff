package com.groovsta.messaging.repository

import com.groovsta.messaging.model.ChatMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {
    fun findBySenderAccountIdAndClientMessageId(senderAccountId: UUID, clientMessageId: UUID): ChatMessage?

    fun findByConversationIdOrderByCreatedAtDescIdDesc(
        conversationId: UUID,
        pageable: Pageable
    ): List<ChatMessage>

    @Query(
        """
        select message from ChatMessage message
        where message.conversationId = :conversationId
          and (
              message.createdAt < :createdAt
              or (message.createdAt = :createdAt and message.id < :id)
          )
        order by message.createdAt desc, message.id desc
        """
    )
    fun findPageAfter(
        @Param("conversationId") conversationId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ChatMessage>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE chat_messages
        SET delivered_at = :deliveredAt
        WHERE conversation_id = :conversationId
          AND sender_account_id <> :recipientAccountId
          AND created_at <= :throughCreatedAt
          AND delivered_at IS NULL
        """,
        nativeQuery = true
    )
    fun markIncomingDeliveredThrough(
        @Param("conversationId") conversationId: UUID,
        @Param("recipientAccountId") recipientAccountId: UUID,
        @Param("throughCreatedAt") throughCreatedAt: Instant,
        @Param("deliveredAt") deliveredAt: Instant
    ): Int
}
