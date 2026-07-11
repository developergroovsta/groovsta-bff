package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class AbstractModel(
    @Id
    open var id: UUID = UUID.randomUUID()
)

@MappedSuperclass
abstract class CreatedAtModel(
    id: UUID = UUID.randomUUID(),
    @Column(name = "created_at", nullable = false, updatable = false)
    open var createdAt: Instant = Instant.EPOCH
) : AbstractModel(id) {

    @PrePersist
    open fun onCreate() {
        createdAt = Instant.now()
    }
}

@MappedSuperclass
abstract class AuditableModel(
    id: UUID = UUID.randomUUID(),
    createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: Instant = Instant.EPOCH
) : CreatedAtModel(id, createdAt) {

    @PrePersist
    override fun onCreate() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    open fun onUpdate() {
        updatedAt = Instant.now()
    }
}
