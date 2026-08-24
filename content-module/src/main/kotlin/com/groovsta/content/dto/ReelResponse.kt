package com.groovsta.content.dto

import com.groovsta.content.model.ContentStatus
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.time.Instant
import java.util.UUID

data class ReelResponse(
    val id: UUID,
    val ownerAccountId: UUID,
    val caption: String?,
    val status: ContentStatus,
    val playbackUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val taggedAccounts: List<ProfileSummaryResponse>,
    val canShareToStory: Boolean,
    val likeCount: Long,
    val liked: Boolean,
    val commentCount: Long,
    val publishedAt: Instant?,
    val createdAt: Instant,
    val moderationReason: String?,
    val saved: Boolean?
)
