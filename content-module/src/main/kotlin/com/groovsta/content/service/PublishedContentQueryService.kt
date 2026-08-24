package com.groovsta.content.service

import com.groovsta.content.dto.ContentFeedItemResponse
import java.util.UUID

interface PublishedContentQueryService {
    fun homeFeed(
        viewerAccountId: UUID?,
        priorityOwnerIds: Set<UUID>,
        mutedPostOwnerIds: Set<UUID>,
        mutedReelOwnerIds: Set<UUID>,
        cursor: String?,
        limit: Int
    ): PublishedContentPage
}

data class PublishedContentPage(
    val items: List<ContentFeedItemResponse>,
    val nextCursor: String?
)
