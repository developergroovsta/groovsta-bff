package com.groovsta.content.dto

import java.util.UUID

data class CommentLikeResponse(
    val commentId: UUID,
    val liked: Boolean,
    val likeCount: Long,
    val likeCountDisplay: String = likeCount.toDisplayCount()
)
