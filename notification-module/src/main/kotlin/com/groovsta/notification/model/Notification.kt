package com.groovsta.notification.model

import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notifications")
class Notification(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "recipient_account_id", nullable = false)
    var recipientAccountId: UUID = UUID.randomUUID(),
    @Column(name = "actor_account_id")
    var actorAccountId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    var type: NotificationType = NotificationType.DIRECT_MESSAGE,
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    var targetType: NotificationTargetType = NotificationTargetType.CONVERSATION,
    @Column(name = "target_id", nullable = false)
    var targetId: UUID = UUID.randomUUID(),
    @Column(name = "reference_id", nullable = false)
    var referenceId: UUID = UUID.randomUUID(),
    @Column(name = "context_id")
    var contextId: UUID? = null,
    @Column(length = 280)
    var preview: String? = null,
    @Column(name = "read_at")
    var readAt: Instant? = null,
    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
) {
    fun markRead(now: Instant) {
        if (readAt == null) readAt = now
    }

    fun resolve(now: Instant) {
        if (resolvedAt == null) resolvedAt = now
    }
}
