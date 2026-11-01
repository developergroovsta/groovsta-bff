package com.groovsta.notification.repository

import com.groovsta.notification.model.PushDelivery
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PushDeliveryRepository : JpaRepository<PushDelivery, UUID> {
    @Modifying
    @Query(
        value = """
        INSERT INTO notification_push_deliveries (
            id, push_registration_id, recipient_account_id, notification_id,
            deduplication_key, message_type, title, body, data_payload,
            high_priority, ttl_seconds, collapse_key, status, attempt_count,
            next_attempt_at, created_at, updated_at
        ) VALUES (
            :id, :pushRegistrationId, :recipientAccountId, :notificationId,
            :deduplicationKey, :messageType, :title, :body, :dataPayload,
            :highPriority, :ttlSeconds, :collapseKey, 'PENDING', 0,
            :now, :now, :now
        )
        ON CONFLICT (push_registration_id, deduplication_key) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("pushRegistrationId") pushRegistrationId: UUID,
        @Param("recipientAccountId") recipientAccountId: UUID,
        @Param("notificationId") notificationId: UUID?,
        @Param("deduplicationKey") deduplicationKey: String,
        @Param("messageType") messageType: String,
        @Param("title") title: String?,
        @Param("body") body: String?,
        @Param("dataPayload") dataPayload: String,
        @Param("highPriority") highPriority: Boolean,
        @Param("ttlSeconds") ttlSeconds: Long,
        @Param("collapseKey") collapseKey: String?,
        @Param("now") now: Instant
    ): Int

    @Modifying
    @Query(
        """
        update PushDelivery delivery
        set delivery.status = com.groovsta.notification.model.PushDeliveryStatus.SKIPPED,
            delivery.lockedUntil = null,
            delivery.lastError = :reason,
            delivery.updatedAt = :now
        where delivery.pushRegistrationId = :registrationId
          and delivery.status in (
            com.groovsta.notification.model.PushDeliveryStatus.PENDING,
            com.groovsta.notification.model.PushDeliveryStatus.RETRY,
            com.groovsta.notification.model.PushDeliveryStatus.PROCESSING
          )
        """
    )
    fun skipOpenByRegistrationId(
        @Param("registrationId") registrationId: UUID,
        @Param("reason") reason: String,
        @Param("now") now: Instant
    ): Int
}
