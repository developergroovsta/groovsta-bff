package com.groovsta.content.dto

import com.groovsta.content.model.ContentKind
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.time.Instant
import java.util.UUID

data class ContentFeedItemResponse(
    val id: UUID,
    val type: ContentKind,
    val author: ProfileSummaryResponse,
    val isFollowingAuthor: Boolean?,
    val canShareToStory: Boolean,
    val saved: Boolean?,
    val savedAt: Instant?,
    val post: PostResponse?,
    val reel: ReelResponse?,
    val publishedAt: Instant
)
