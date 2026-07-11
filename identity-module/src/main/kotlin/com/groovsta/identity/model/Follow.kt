package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_follows")
class Follow(
    id: UUID = UUID.randomUUID(),
    @Column(name = "follower_account_id", nullable = false)
    var followerAccountId: UUID = UUID.randomUUID(),
    @Column(name = "followed_account_id", nullable = false)
    var followedAccountId: UUID = UUID.randomUUID(),
    createdAt: Instant = Instant.EPOCH
) : CreatedAtModel(id, createdAt)
