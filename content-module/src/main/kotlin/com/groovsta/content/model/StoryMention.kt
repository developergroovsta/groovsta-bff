package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_story_mentions")
class StoryMention(
    id: UUID = UUID.randomUUID(),
    @Column(name = "story_id", nullable = false)
    var storyId: UUID = UUID.randomUUID(),
    @Column(name = "mentioned_account_id", nullable = false)
    var mentionedAccountId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 30)
    var handle: String = "",
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
