package com.groovsta.notification.repository

import com.groovsta.notification.model.Notification
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {

    fun findByTypeAndReferenceId(
        type: com.groovsta.shared.notification.NotificationType,
        referenceId: UUID
    ): Notification?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO notifications (
                id, recipient_account_id, actor_account_id, notification_type,
                target_type, target_id, reference_id, context_id, preview, created_at
            )
            VALUES (
                :id, :recipientAccountId, :actorAccountId, :notificationType,
                :targetType, :targetId, :referenceId, :contextId, :preview, :createdAt
            )
            ON CONFLICT (notification_type, reference_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("recipientAccountId") recipientAccountId: UUID,
        @Param("actorAccountId") actorAccountId: UUID,
        @Param("notificationType") notificationType: String,
        @Param("targetType") targetType: String,
        @Param("targetId") targetId: UUID,
        @Param("referenceId") referenceId: UUID,
        @Param("contextId") contextId: UUID?,
        @Param("preview") preview: String?,
        @Param("createdAt") createdAt: Instant
    ): Int

    @Query(
        """
        select notification from Notification notification
        where notification.recipientAccountId = :recipientAccountId
          and (:unreadOnly = false or notification.readAt is null)
        order by notification.createdAt desc, notification.id desc
        """
    )
    fun findInbox(
        @Param("recipientAccountId") recipientAccountId: UUID,
        @Param("unreadOnly") unreadOnly: Boolean,
        pageable: Pageable
    ): List<Notification>

    @Query(
        """
        select notification from Notification notification
        where notification.recipientAccountId = :recipientAccountId
          and (:unreadOnly = false or notification.readAt is null)
          and (
            notification.createdAt < :createdAt
            or (notification.createdAt = :createdAt and notification.id < :id)
          )
        order by notification.createdAt desc, notification.id desc
        """
    )
    fun findInboxAfter(
        @Param("recipientAccountId") recipientAccountId: UUID,
        @Param("unreadOnly") unreadOnly: Boolean,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<Notification>

    fun countByRecipientAccountIdAndReadAtIsNull(recipientAccountId: UUID): Long

    @Modifying
    @Query(
        """
        update Notification notification
        set notification.readAt = :readAt
        where notification.recipientAccountId = :recipientAccountId
          and notification.readAt is null
        """
    )
    fun markAllRead(
        @Param("recipientAccountId") recipientAccountId: UUID,
        @Param("readAt") readAt: Instant
    ): Int

    @Modifying
    @Query(
        """
        update Notification notification
        set notification.resolvedAt = :resolvedAt
        where notification.type = :notificationType
          and notification.referenceId = :referenceId
          and notification.resolvedAt is null
        """
    )
    fun resolve(
        @Param("notificationType") notificationType: com.groovsta.shared.notification.NotificationType,
        @Param("referenceId") referenceId: UUID,
        @Param("resolvedAt") resolvedAt: Instant
    ): Int
}
