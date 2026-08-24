package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_saved_items")
class SavedContent(
    id: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(name = "content_id", nullable = false)
    var contentId: UUID = UUID.randomUUID(),
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
