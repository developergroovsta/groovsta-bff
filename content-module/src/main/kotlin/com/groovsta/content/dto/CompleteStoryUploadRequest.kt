package com.groovsta.content.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CompleteStoryUploadRequest(
    @field:NotBlank
    @field:Size(max = 1024)
    val objectKey: String
)
