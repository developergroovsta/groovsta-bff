package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_sessions")
class Session(
    id: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(name = "device_id")
    var deviceId: UUID? = null,
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String = "",
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH,
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.EPOCH,
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    createdAt: Instant = Instant.EPOCH
) : CreatedAtModel(id, createdAt) {

    @PrePersist
    fun initializeLastSeen() {
        lastSeenAt = Instant.now()
    }

    fun isUsableAt(now: Instant): Boolean = revokedAt == null && expiresAt.isAfter(now)

    fun markSeen() {
        lastSeenAt = Instant.now()
    }

    fun revoke() {
        revokedAt = Instant.now()
    }
}
