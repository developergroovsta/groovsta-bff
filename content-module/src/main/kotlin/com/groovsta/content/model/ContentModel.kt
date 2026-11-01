package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class ContentModel(
    @Id open var id: UUID = UUID.randomUUID(),
    @Column(name = "created_at", nullable = false) open var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false) open var updatedAt: Instant = Instant.EPOCH
)
