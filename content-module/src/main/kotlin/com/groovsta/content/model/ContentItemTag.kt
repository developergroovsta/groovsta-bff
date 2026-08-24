package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "content_item_tags",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["content_id", "tagged_account_id"]),
        UniqueConstraint(columnNames = ["content_id", "position"])
    ]
)
class ContentItemTag(
    id: UUID = UUID.randomUUID(),
    @Column(name = "content_id", nullable = false)
    var contentId: UUID = UUID.randomUUID(),
    @Column(name = "tagged_account_id", nullable = false)
    var taggedAccountId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 30)
    var handle: String = "",
    @Column(nullable = false)
    var position: Int = 0,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
