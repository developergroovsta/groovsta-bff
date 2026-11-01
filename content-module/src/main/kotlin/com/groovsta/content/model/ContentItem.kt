package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_items")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "content_kind", discriminatorType = DiscriminatorType.STRING, length = 20)
abstract class ContentItem(
    id: UUID = UUID.randomUUID(),
    @Column(name = "owner_account_id", nullable = false)
    open var ownerAccountId: UUID = UUID.randomUUID(),
    @Column(length = 2200)
    open var caption: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    open var status: ContentStatus = ContentStatus.UPLOADING,
    @Column(name = "moderation_reason", length = 1000)
    open var moderationReason: String? = null,
    @Column(name = "published_at")
    open var publishedAt: Instant? = null,
    @Column(name = "location_id")
    open var locationId: UUID? = null,
    @Column(name = "comments_enabled", nullable = false)
    open var commentsEnabled: Boolean = true,
    @Column(name = "hide_like_count", nullable = false)
    open var hideLikeCount: Boolean = false,
    @Column(name = "hide_share_count", nullable = false)
    open var hideShareCount: Boolean = false,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
