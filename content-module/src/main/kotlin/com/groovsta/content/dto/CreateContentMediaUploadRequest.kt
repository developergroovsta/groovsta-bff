package com.groovsta.content.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateContentMediaUploadRequest(
    @NotBlank
    @Size(max = 255)
    val fileName: String,
    @NotBlank
    @Size(max = 100)
    val contentType: String,
    @Positive
    @Max(5368709120)
    val sizeBytes: Long
)
