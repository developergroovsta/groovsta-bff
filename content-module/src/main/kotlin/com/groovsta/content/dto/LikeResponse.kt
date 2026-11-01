package com.groovsta.content.dto

import java.util.UUID

data class LikeResponse(
    val contentId: UUID,
    val liked: Boolean,
    val likeCount: Long,
    val likeCountDisplay: String = likeCount.toDisplayCount()
)
