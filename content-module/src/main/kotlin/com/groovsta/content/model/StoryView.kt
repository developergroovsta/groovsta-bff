package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_story_views")
class StoryView(
    id: UUID = UUID.randomUUID(),
    @Column(name = "story_id", nullable = false)
    var storyId: UUID = UUID.randomUUID(),
    @Column(name = "viewer_account_id", nullable = false)
    var viewerAccountId: UUID = UUID.randomUUID(),
    @Column(name = "viewed_at", nullable = false)
    var viewedAt: Instant = Instant.now()
) : ContentModel(id, viewedAt, viewedAt)
