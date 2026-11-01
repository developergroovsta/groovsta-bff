package com.groovsta.content.dto

import com.groovsta.content.model.StoryVisibility
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateStoryRequest(
    val visibility: StoryVisibility,
    @field:Size(max = 2200)
    val caption: String? = null,
    @field:Valid
    val media: CreateStoryMediaRequest? = null,
    val linkedContentId: UUID? = null,
    val linkedStoryId: UUID? = null,
    @field:Size(max = 20)
    val taggedAccountIds: List<UUID> = emptyList(),
    val locationId: UUID? = null,
    val commentsEnabled: Boolean = true,
    val hideComments: Boolean = false,
    val hideLikeCount: Boolean = false,
    val hideShareCount: Boolean = false
)
