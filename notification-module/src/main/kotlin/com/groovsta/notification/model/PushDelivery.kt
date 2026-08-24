package com.groovsta.notification.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notification_push_deliveries")
class PushDelivery(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "push_registration_id", nullable = false)
    var pushRegistrationId: UUID = UUID.randomUUID(),
    @Column(name = "recipient_account_id", nullable = false)
    var recipientAccountId: UUID = UUID.randomUUID(),
    @Column(name = "notification_id")
    var notificationId: UUID? = null,
    @Column(name = "deduplication_key", nullable = false, length = 255)
    var deduplicationKey: String = "",
    @Column(name = "message_type", nullable = false, length = 50)
    var messageType: String = "NOTIFICATION",
    @Column(length = 160)
    var title: String? = null,
    @Column(length = 320)
    var body: String? = null,
    @Column(name = "data_payload", nullable = false, columnDefinition = "TEXT")
    var dataPayload: String = "{}",
    @Column(name = "high_priority", nullable = false)
    var highPriority: Boolean = false,
    @Column(name = "ttl_seconds", nullable = false)
    var ttlSeconds: Long = 0,
    @Column(name = "collapse_key", length = 255)
    var collapseKey: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PushDeliveryStatus = PushDeliveryStatus.PENDING,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,
    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),
    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,
    @Column(name = "sent_at")
    var sentAt: Instant? = null,
    @Column(name = "provider_message_id", length = 512)
    var providerMessageId: String? = null,
    @Column(name = "last_error", length = 1000)
    var lastError: String? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
