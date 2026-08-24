package com.groovsta.content.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateReelUploadRequest(
    @NotBlank @Size(max = 255) val fileName: String,
    @Pattern(regexp = "video/(mp4|quicktime|webm)") val contentType: String,
    @Positive @Max(5368709120) val sizeBytes: Long,
    @Size(max = 2200) val caption: String?,
    @Size(max = 20)
    val taggedAccountIds: List<UUID> = emptyList()
)
