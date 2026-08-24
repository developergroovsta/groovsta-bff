package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_close_friends")
class CloseFriend(
    id: UUID = UUID.randomUUID(),
    @Column(name = "owner_account_id", nullable = false)
    var ownerAccountId: UUID = UUID.randomUUID(),
    @Column(name = "friend_account_id", nullable = false)
    var friendAccountId: UUID = UUID.randomUUID(),
    createdAt: Instant = Instant.EPOCH
) : CreatedAtModel(id, createdAt)
