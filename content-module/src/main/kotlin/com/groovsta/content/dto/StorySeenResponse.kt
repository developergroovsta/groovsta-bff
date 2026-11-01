package com.groovsta.content.dto

import java.time.Instant
import java.util.UUID

data class StorySeenResponse(
    val storyId: UUID,
    val hasSeen: Boolean,
    val seenAt: Instant
)
