package com.groovsta.identity.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CompleteAvatarUploadRequest(
    @NotBlank
    @Size(max = 1024)
    val objectKey: String
)
