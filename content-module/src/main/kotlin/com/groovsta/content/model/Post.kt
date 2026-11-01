package com.groovsta.content.model

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.Instant
import java.util.UUID

@Entity
@DiscriminatorValue("POST")
class Post(
    id: UUID = UUID.randomUUID(),
    ownerAccountId: UUID = UUID.randomUUID(),
    caption: String? = null,
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
