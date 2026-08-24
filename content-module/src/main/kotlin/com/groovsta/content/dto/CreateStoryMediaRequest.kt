package com.groovsta.content.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateStoryMediaRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val fileName: String,
    @field:NotBlank
    @field:Size(max = 100)
    val contentType: String,
    @field:Positive
    val sizeBytes: Long
)
