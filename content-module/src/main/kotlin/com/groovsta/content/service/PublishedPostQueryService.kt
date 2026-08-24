package com.groovsta.content.service

import com.groovsta.content.dto.PostResponse
import java.time.Instant
import java.util.UUID

interface PublishedPostQueryService {
    fun latestPosts(viewerAccountId: UUID?, before: Instant, limit: Int): List<PostResponse>
    fun latestPostsByOwners(
        viewerAccountId: UUID?,
        ownerIds: Set<UUID>,
        before: Instant,
        limit: Int
    ): List<PostResponse>
}
