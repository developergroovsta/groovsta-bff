package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "content_likes",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["content_id", "account_id"]),
        UniqueConstraint(columnNames = ["story_id", "account_id"])
    ]
)
class ContentLike(
    id: UUID = UUID.randomUUID(),
    @Column(name = "content_id") var contentId: UUID? = null,
    @Column(name = "story_id") var storyId: UUID? = null,
    @Column(name = "account_id", nullable = false) var accountId: UUID = UUID.randomUUID(),
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
