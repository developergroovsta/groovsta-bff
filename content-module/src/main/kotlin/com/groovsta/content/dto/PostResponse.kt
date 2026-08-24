package com.groovsta.content.dto

import com.groovsta.content.model.ContentStatus
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.time.Instant
import java.util.UUID

data class PostResponse(
    val id: UUID,
    val ownerAccountId: UUID,
    val caption: String?,
    val status: ContentStatus,
    val media: List<PostMediaResponse>,
    val taggedAccounts: List<ProfileSummaryResponse>,
    val canShareToStory: Boolean,
    val likeCount: Long,
    val liked: Boolean,
    val commentCount: Long,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val saved: Boolean?
)
