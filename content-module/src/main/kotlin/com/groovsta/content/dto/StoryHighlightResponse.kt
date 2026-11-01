package com.groovsta.content.dto

import java.time.Instant
import java.util.UUID

data class StoryHighlightResponse(
    val id: UUID,
    val name: String,
    val stories: List<StoryResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)
