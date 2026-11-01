package com.groovsta.content.dto

import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.time.Instant
import java.util.UUID

data class CommentResponse(
    val id: UUID,
    val contentId: UUID?,
    val storyId: UUID?,
    val threadRootId: UUID,
    val replyToCommentId: UUID?,
    val depth: Int,
    val accountId: UUID,
    val author: ProfileSummaryResponse,
    val text: String,
    val mentions: List<CommentMentionResponse>,
    val replyCount: Long,
    val replyCountDisplay: String,
    val likeCount: Long,
    val likeCountDisplay: String,
    val createdAt: Instant,
    val isDeleted: Boolean,
    val viewerState: CommentViewerStateResponse
)

data class CommentMentionResponse(
    val accountId: UUID,
    val handle: String,
    val start: Int,
    val endExclusive: Int
)

data class CommentViewerStateResponse(
    val canDelete: Boolean,
    val canReply: Boolean,
    val canLike: Boolean,
    val liked: Boolean
)
