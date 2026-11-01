package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_post_media")
class PostMedia(
    id: UUID = UUID.randomUUID(),
    @Column(name = "post_id", nullable = false) var postId: UUID = UUID.randomUUID(),
    @Column(name = "object_key", nullable = false, length = 1024) var objectKey: String = "",
    @Column(name = "content_type", nullable = false, length = 100) var contentType: String = "image/jpeg",
    @Column(name = "size_bytes", nullable = false) var sizeBytes: Long = 0,
    @Column(name = "position", nullable = false) var position: Int = 0,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
