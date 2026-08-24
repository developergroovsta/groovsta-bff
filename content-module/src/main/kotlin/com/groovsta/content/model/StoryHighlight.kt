package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_story_highlights")
class StoryHighlight(
    id: UUID = UUID.randomUUID(),
    @Column(name = "owner_account_id", nullable = false)
    var ownerAccountId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 80)
    var name: String = "",
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
