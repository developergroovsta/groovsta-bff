package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "content_comment_likes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["comment_id", "account_id"])]
)
class ContentCommentLike(
    id: UUID = UUID.randomUUID(),
    @Column(name = "comment_id", nullable = false) var commentId: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false) var accountId: UUID = UUID.randomUUID(),
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
