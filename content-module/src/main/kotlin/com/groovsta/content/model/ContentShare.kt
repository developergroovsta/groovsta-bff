package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "content_shares",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["content_id", "account_id"]),
        UniqueConstraint(columnNames = ["story_id", "account_id"])
    ]
)
class ContentShare(
    id: UUID = UUID.randomUUID(),
    @Column(name = "content_id") var contentId: UUID? = null,
    @Column(name = "story_id") var storyId: UUID? = null,
    @Column(name = "account_id", nullable = false) var accountId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) var channel: ContentShareChannel = ContentShareChannel.OTHER,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
