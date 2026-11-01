package com.groovsta.content.dto

import java.time.Instant
import java.util.UUID

data class StoryHighlightSummaryResponse(
    val id: UUID,
    val name: String,
    val coverStory: StoryResponse?,
    val storyCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)
