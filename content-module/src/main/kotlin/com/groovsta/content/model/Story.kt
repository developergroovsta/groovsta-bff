package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_stories")
class Story(
    id: UUID = UUID.randomUUID(),
    @Column(name = "owner_account_id", nullable = false)
    var ownerAccountId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    var mediaType: StoryMediaType = StoryMediaType.IMAGE,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var visibility: StoryVisibility = StoryVisibility.EVERYONE,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: StoryStatus = StoryStatus.UPLOADING,
    @Column(length = 2200)
    var caption: String? = null,
    @Column(name = "source_object_key", nullable = false, length = 1024)
    var sourceObjectKey: String = "",
    @Column(name = "published_object_key", length = 1024)
    var publishedObjectKey: String? = null,
    @Column(name = "thumbnail_object_key", length = 1024)
    var thumbnailObjectKey: String? = null,
    @Column(name = "content_type", nullable = false, length = 100)
    var contentType: String = "image/jpeg",
    @Column(name = "source_size_bytes", nullable = false)
    var sourceSizeBytes: Long = 0,
    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,
    @Column(name = "linked_content_id")
    var linkedContentId: UUID? = null,
    @Column(name = "linked_story_id")
    var linkedStoryId: UUID? = null,
    @Column(name = "location_id")
    var locationId: UUID? = null,
    @Column(name = "owns_media", nullable = false)
    var ownsMedia: Boolean = true,
    @Column(name = "comments_enabled", nullable = false)
    var commentsEnabled: Boolean = true,
    @Column(name = "hide_like_count", nullable = false)
    var hideLikeCount: Boolean = false,
    @Column(name = "hide_share_count", nullable = false)
    var hideShareCount: Boolean = false,
    @Column(name = "failure_reason", length = 1000)
    var failureReason: String? = null,
    @Column(name = "moderation_reason", length = 1000)
    var moderationReason: String? = null,
    @Column(name = "published_at")
    var publishedAt: Instant? = null,
    @Column(name = "expires_at")
    var expiresAt: Instant? = null,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
