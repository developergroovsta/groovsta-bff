package com.groovsta.messaging.model

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class MessagingModel(
    @Id
    open var id: UUID = UUID.randomUUID(),
    @Column(name = "created_at", nullable = false, updatable = false)
    open var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.EPOCH
) {
    @PrePersist
    open fun onCreate() {
        val now = Instant.now()
        if (createdAt.equals(Instant.EPOCH)) createdAt = now
        if (updatedAt.equals(Instant.EPOCH)) updatedAt = createdAt
    }

    @PreUpdate
    open fun onUpdate() {
        updatedAt = Instant.now()
    }
}
