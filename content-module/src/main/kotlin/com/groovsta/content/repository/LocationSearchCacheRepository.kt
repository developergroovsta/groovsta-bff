package com.groovsta.content.repository

import com.groovsta.content.model.LocationSearchCache
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional
import java.util.UUID

interface LocationSearchCacheRepository : JpaRepository<LocationSearchCache, UUID> {
    fun findByCacheKey(cacheKey: String): Optional<LocationSearchCache>
    fun findByCacheKeyAndExpiresAtAfter(cacheKey: String, now: Instant): Optional<LocationSearchCache>
}
