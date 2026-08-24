package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_story_highlight_items")
class StoryHighlightItem(
    id: UUID = UUID.randomUUID(),
    @Column(name = "highlight_id", nullable = false)
    var highlightId: UUID = UUID.randomUUID(),
    @Column(name = "story_id", nullable = false)
    var storyId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var position: Int = 0,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
