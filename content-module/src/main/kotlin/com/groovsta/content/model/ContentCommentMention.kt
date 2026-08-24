package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_comment_mentions")
class ContentCommentMention(
    id: UUID = UUID.randomUUID(),
    @Column(name = "comment_id", nullable = false) var commentId: UUID = UUID.randomUUID(),
    @Column(name = "mentioned_account_id", nullable = false) var mentionedAccountId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 30) var handle: String = "",
    @Column(name = "start_index", nullable = false) var startIndex: Int = 0,
    @Column(name = "end_exclusive", nullable = false) var endExclusive: Int = 0,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
