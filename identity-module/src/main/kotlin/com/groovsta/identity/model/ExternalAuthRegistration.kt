package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_external_auth_registrations")
class ExternalAuthRegistration(
    id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var provider: ExternalAuthProvider = ExternalAuthProvider.GOOGLE,
    @Column(name = "provider_subject", nullable = false, length = 128)
    var providerSubject: String = "",
    @Column(nullable = false, length = 320)
    var email: String = "",
    @Column(name = "suggested_display_name", length = 80)
    var suggestedDisplayName: String? = null,
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String = "",
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH,
    @Column(name = "consumed_at")
    var consumedAt: Instant? = null,
    @Column(name = "completed_account_id")
    var completedAccountId: UUID? = null,
    createdAt: Instant = Instant.EPOCH
) : CreatedAtModel(id, createdAt) {

    fun isOpenAt(now: Instant): Boolean = consumedAt == null && expiresAt.isAfter(now)

    fun invalidate(at: Instant) {
        if (consumedAt == null) consumedAt = at
    }

    fun complete(accountId: UUID, at: Instant) {
        completedAccountId = accountId
        consumedAt = at
    }
}
