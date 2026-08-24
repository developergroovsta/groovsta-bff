package com.groovsta.notification.model

import com.groovsta.identity.model.DevicePlatform
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notification_push_registrations")
class PushRegistration(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(name = "identity_device_id", nullable = false)
    var identityDeviceId: UUID = UUID.randomUUID(),
    @Column(name = "registration_id", nullable = false, length = 4096)
    var registrationId: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "identifier_type", nullable = false, length = 40)
    var identifierType: PushIdentifierType = PushIdentifierType.FCM_TOKEN,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var platform: DevicePlatform = DevicePlatform.UNKNOWN,
    @Column(name = "disabled_at")
    var disabledAt: Instant? = null,
    @Column(name = "failure_count", nullable = false)
    var failureCount: Int = 0,
    @Column(name = "last_failure_at")
    var lastFailureAt: Instant? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH
) {
    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        if (createdAt.equals(Instant.EPOCH)) createdAt = now
        if (updatedAt.equals(Instant.EPOCH)) updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    fun register(registrationId: String, identifierType: PushIdentifierType, platform: DevicePlatform) {
        this.registrationId = registrationId
        this.identifierType = identifierType
        this.platform = platform
        disabledAt = null
        failureCount = 0
        lastFailureAt = null
    }

    fun disable(now: Instant = Instant.now()) {
        if (disabledAt == null) disabledAt = now
    }

    fun recordSuccess() {
        failureCount = 0
        lastFailureAt = null
    }

    fun recordFailure(now: Instant) {
        failureCount += 1
        lastFailureAt = now
    }
}
