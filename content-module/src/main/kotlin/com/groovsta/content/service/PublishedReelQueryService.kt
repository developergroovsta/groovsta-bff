package com.groovsta.content.service

import com.groovsta.content.dto.ReelResponse
import java.time.Instant
import java.util.UUID

interface PublishedReelQueryService {
    fun latest(viewerAccountId: UUID?, before: Instant, limit: Int): List<ReelResponse>
    fun latestExcludingOwners(
        viewerAccountId: UUID?,
        before: Instant,
        excludedOwnerIds: Set<UUID>,
        limit: Int
    ): List<ReelResponse>
    fun latestByOwners(
        viewerAccountId: UUID?,
        ownerIds: Set<UUID>,
        before: Instant,
        limit: Int
    ): List<ReelResponse>
}
