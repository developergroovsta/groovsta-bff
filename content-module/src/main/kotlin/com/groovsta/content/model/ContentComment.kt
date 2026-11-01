package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_comments")
class ContentComment(
    id: UUID = UUID.randomUUID(),
    @Column(name = "content_id") var contentId: UUID? = null,
    @Column(name = "story_id") var storyId: UUID? = null,
    @Column(name = "account_id", nullable = false) var accountId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 500) var text: String = "",
    @Column(name = "thread_root_id", nullable = false) var threadRootId: UUID = id,
    @Column(name = "reply_to_comment_id") var replyToCommentId: UUID? = null,
    @Column(nullable = false) var depth: Int = 0,
    @Column(name = "deleted_at") var deletedAt: Instant? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
