package com.groovsta.content.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreatePostMediaUploadRequest(
    @NotBlank
    @Size(max = 255)
    val fileName: String,
    @Pattern(regexp = "image/(jpeg|png|webp)", message = "Post image must be JPEG, PNG, or WebP")
    val contentType: String,
    @Positive
    @Max(52428800)
    val sizeBytes: Long
)
