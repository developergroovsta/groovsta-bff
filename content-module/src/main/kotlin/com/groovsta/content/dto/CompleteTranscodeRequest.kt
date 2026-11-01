package com.groovsta.content.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class CompleteTranscodeRequest(
    @NotBlank val publishedObjectKey: String,
    val thumbnailObjectKey: String?,
    @Positive val durationSeconds: Int,
    @Positive val width: Int? = null,
    @Positive val height: Int? = null
)
