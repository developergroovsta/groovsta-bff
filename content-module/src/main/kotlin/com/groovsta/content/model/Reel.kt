package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.Instant
import java.util.UUID

@Entity
@DiscriminatorValue("REEL")
class Reel(
    id: UUID = UUID.randomUUID(),
    ownerAccountId: UUID = UUID.randomUUID(),
    caption: String? = null,
    @Column(name = "source_object_key", nullable = false, length = 1024) var sourceObjectKey: String = "",
    @Column(name = "published_object_key", length = 1024) var publishedObjectKey: String? = null,
    @Column(name = "thumbnail_object_key", length = 1024) var thumbnailObjectKey: String? = null,
    @Column(name = "content_type", nullable = false, length = 100) var contentType: String = "video/mp4",
    @Column(name = "source_size_bytes", nullable = false) var sourceSizeBytes: Long = 0,
    @Column(name = "duration_seconds") var durationSeconds: Int? = null,
    status: ContentStatus = ContentStatus.UPLOADING,
    moderationReason: String? = null,
    publishedAt: Instant? = null,
    locationId: UUID? = null,
    commentsEnabled: Boolean = true,
    hideLikeCount: Boolean = false,
    hideShareCount: Boolean = false,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentItem(
    id = id,
    ownerAccountId = ownerAccountId,
    caption = caption,
    status = status,
    moderationReason = moderationReason,
    publishedAt = publishedAt,
    locationId = locationId,
    commentsEnabled = commentsEnabled,
    hideLikeCount = hideLikeCount,
    hideShareCount = hideShareCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)
