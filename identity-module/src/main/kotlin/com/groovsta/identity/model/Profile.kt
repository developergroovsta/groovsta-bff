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
    @Column(name = "avatar_object_key", length = 1024)
    var avatarObjectKey: String? = null,
    @Column(name = "is_public", nullable = false)
    var isPublic: Boolean = true,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : AuditableModel(id, createdAt, updatedAt) {

    fun update(displayName: String, bio: String?, isPublic: Boolean?) {
        this.displayName = displayName
        this.bio = bio
        isPublic?.let { this.isPublic = it }
    }
}
