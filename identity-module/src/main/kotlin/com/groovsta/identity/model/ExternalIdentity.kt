package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_external_identities")
class ExternalIdentity(
    id: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var provider: ExternalAuthProvider = ExternalAuthProvider.GOOGLE,
    @Column(name = "provider_subject", nullable = false, length = 128)
    var providerSubject: String = "",
    @Column(name = "provider_email", nullable = false, length = 320)
    var providerEmail: String = "",
    @Column(name = "last_login_at", nullable = false)
    var lastLoginAt: Instant = Instant.EPOCH,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : AuditableModel(id, createdAt, updatedAt) {

    fun recordLogin(email: String, at: Instant) {
        providerEmail = email
        lastLoginAt = at
    }
}
