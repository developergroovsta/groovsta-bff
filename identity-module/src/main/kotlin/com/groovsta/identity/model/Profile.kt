package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_profiles")
class Profile(
    id: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false, unique = true)
    var accountId: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, length = 30)
    var handle: String = "",
    @Column(name = "display_name", nullable = false, length = 80)
    var displayName: String = "",
    @Column(length = 500)
    var bio: String? = null,
    @Column(name = "avatar_url", length = 2048)
    var avatarUrl: String? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : AuditableModel(id, createdAt, updatedAt) {

    fun update(displayName: String, bio: String?, avatarUrl: String?) {
        this.displayName = displayName
        this.bio = bio
        this.avatarUrl = avatarUrl
    }
}
