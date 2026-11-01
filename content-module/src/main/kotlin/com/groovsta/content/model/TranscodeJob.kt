package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_transcode_jobs")
class TranscodeJob(
    id: UUID = UUID.randomUUID(),
    @Column(name = "reel_id", unique = true) var reelId: UUID? = null,
    @Column(name = "story_id", unique = true) var storyId: UUID? = null,
    @Column(name = "chat_attachment_id", unique = true) var chatAttachmentId: UUID? = null,
    @Column(name = "source_object_key", nullable = false, length = 1024) var sourceObjectKey: String = "",
    @Column(name = "output_prefix", nullable = false, length = 1024) var outputPrefix: String = "",
    @Column(nullable = false, length = 20) var status: String = "QUEUED",
    @Column(name = "claimed_at") var claimedAt: Instant? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
