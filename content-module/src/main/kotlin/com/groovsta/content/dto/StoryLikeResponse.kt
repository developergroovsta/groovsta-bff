package com.groovsta.content.dto

import java.util.UUID

data class StoryLikeResponse(
    val storyId: UUID,
    val liked: Boolean,
    val likeCount: Long,
    val likeCountDisplay: String = likeCount.toDisplayCount()
)
