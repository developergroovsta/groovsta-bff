package com.groovsta.identity.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateAvatarUploadRequest(
    @NotBlank
    @Size(max = 255)
    val fileName: String,
    @Pattern(regexp = "image/(jpeg|png|webp)", message = "Avatar must be JPEG, PNG, or WebP")
    val contentType: String
)
