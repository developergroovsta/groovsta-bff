package com.groovsta.content.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "content_location_search_cache")
class LocationSearchCache(
    id: UUID = UUID.randomUUID(),
    @Column(name = "cache_key", nullable = false, unique = true, length = 600)
    var cacheKey: String = "",
    @Column(name = "location_ids", nullable = false, columnDefinition = "TEXT")
    var locationIds: String = "",
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : ContentModel(id, createdAt, updatedAt)
