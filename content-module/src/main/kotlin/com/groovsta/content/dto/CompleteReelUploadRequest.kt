package com.groovsta.content.dto

import jakarta.validation.constraints.NotBlank

data class CompleteReelUploadRequest(@NotBlank val objectKey: String)
