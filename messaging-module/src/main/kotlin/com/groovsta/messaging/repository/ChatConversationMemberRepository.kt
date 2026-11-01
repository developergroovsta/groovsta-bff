package com.groovsta.messaging.repository

import com.groovsta.messaging.model.ChatConversationMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChatConversationMemberRepository : JpaRepository<ChatConversationMember, UUID> {

    fun findByConversationIdAndAccountId(conversationId: UUID, accountId: UUID): ChatConversationMember?

    fun findByConversationIdIn(conversationIds: Collection<UUID>): List<ChatConversationMember>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        INSERT INTO chat_conversation_members (
            id,
            conversation_id,
            account_id,
            created_at,
            updated_at
        )
        VALUES (:id, :conversationId, :accountId, :createdAt, :updatedAt)
        ON CONFLICT (conversation_id, account_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("conversationId") conversationId: UUID,
        @Param("accountId") accountId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("updatedAt") updatedAt: Instant
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
        UPDATE chat_conversation_members
        SET last_read_message_id = :messageId,
            last_read_at = :messageCreatedAt,
            updated_at = :updatedAt
        WHERE conversation_id = :conversationId
          AND account_id = :accountId
          AND (last_read_at IS NULL OR last_read_at < :messageCreatedAt)
        """,
        nativeQuery = true
    )
    fun advanceReadPosition(
        @Param("conversationId") conversationId: UUID,
        @Param("accountId") accountId: UUID,
        @Param("messageId") messageId: UUID,
        @Param("messageCreatedAt") messageCreatedAt: Instant,
        @Param("updatedAt") updatedAt: Instant
    ): Int
}
