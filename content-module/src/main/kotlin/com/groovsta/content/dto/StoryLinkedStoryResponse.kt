package com.groovsta.content.dto

import java.util.UUID

data class StoryLinkedStoryResponse(
    val storyId: UUID,
    val available: Boolean,
    val unavailableMessage: String? = null
)
