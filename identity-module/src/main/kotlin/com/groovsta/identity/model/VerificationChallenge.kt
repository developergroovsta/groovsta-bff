package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_verification_challenges")
class VerificationChallenge(
    id: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var channel: VerificationChannel = VerificationChannel.EMAIL,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var purpose: VerificationPurpose = VerificationPurpose.ACCOUNT_VERIFICATION,
    @Column(nullable = false, length = 320)
    var destination: String = "",
    @Column(name = "code_hash", nullable = false, length = 64)
    var codeHash: String = "",
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH,
    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,
    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,
    @Column(name = "invalidated_at")
    var invalidatedAt: Instant? = null,
    createdAt: Instant = Instant.EPOCH
) : CreatedAtModel(id, createdAt) {

    fun isOpenAt(now: Instant): Boolean = verifiedAt == null && invalidatedAt == null && expiresAt.isAfter(now)

    fun recordFailedAttempt(maxAttempts: Int) {
        attemptCount++
        if (attemptCount >= maxAttempts) {
            invalidatedAt = Instant.now()
        }
    }

    fun verify() {
        verifiedAt = Instant.now()
    }

    fun invalidate() {
        invalidatedAt = Instant.now()
    }
}
