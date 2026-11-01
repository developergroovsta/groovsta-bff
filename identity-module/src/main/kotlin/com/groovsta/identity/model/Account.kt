package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_accounts")
class Account(
    id: UUID = UUID.randomUUID(),
    @Column(unique = true, length = 320)
    var email: String? = null,
    @Column(name = "password_hash", length = 100)
    var passwordHash: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: AccountStatus = AccountStatus.RESTRICTED,
    @Column(name = "email_verified_at")
    var emailVerifiedAt: Instant? = null,
    @Column(name = "phone_number", unique = true, length = 32)
    var phoneNumber: String? = null,
    @Column(name = "phone_verified_at")
    var phoneVerifiedAt: Instant? = null,
    @Column(name = "adult_declared_at", nullable = false)
    var adultDeclaredAt: Instant = Instant.EPOCH,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : AuditableModel(id, createdAt, updatedAt) {

    fun verifyEmail(verifiedAt: Instant) {
        emailVerifiedAt = verifiedAt
        updateActivationStatus()
    }

    fun verifyPhone(verifiedPhoneNumber: String, verifiedAt: Instant) {
        phoneNumber = verifiedPhoneNumber
        phoneVerifiedAt = verifiedAt
        updateActivationStatus()
    }

    private fun updateActivationStatus() {
        if (status != AccountStatus.SUSPENDED
            && emailVerifiedAt != null
            && phoneVerifiedAt != null
        ) {
            status = AccountStatus.ACTIVE
        }
    }
}
