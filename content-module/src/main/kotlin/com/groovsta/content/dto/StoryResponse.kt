package com.groovsta.content.dto

import com.groovsta.content.model.StoryMediaType
import com.groovsta.content.model.StoryStatus
import com.groovsta.content.model.StoryVisibility
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.time.Instant
import java.util.UUID

data class StoryResponse(
    val id: UUID,
    val author: ProfileSummaryResponse,
    val mediaType: StoryMediaType,
    val visibility: StoryVisibility,
    val status: StoryStatus,
    val caption: String?,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val failureReason: String?,
    val moderationReason: String?,
    val likeCount: Long,
    val liked: Boolean,
    val commentCount: Long,
    val linkedContent: StoryLinkedContentResponse?,
    val taggedAccounts: List<ProfileSummaryResponse>,
    val publishedAt: Instant?,
    val expiresAt: Instant?,
    val expired: Boolean,
    val createdAt: Instant,
    val hasSeen: Boolean,
    val viewerState: StoryViewerStateResponse,
    val linkedStory: StoryLinkedStoryResponse? = null
)
