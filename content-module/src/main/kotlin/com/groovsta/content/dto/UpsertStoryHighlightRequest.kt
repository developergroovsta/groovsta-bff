package com.groovsta.content.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class UpsertStoryHighlightRequest(
    @field:NotBlank
    @field:Size(max = 80)
    val name: String,
    @field:Size(min = 1, max = 100)
    val storyIds: List<UUID>
)
