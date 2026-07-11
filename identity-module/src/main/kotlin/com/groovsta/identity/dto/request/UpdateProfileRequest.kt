package com.groovsta.identity.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @NotBlank
    @Size(max = 80)
    val displayName: String,
    @Size(max = 500)
    val bio: String?,
    @Pattern(regexp = "https?://.+", message = "Avatar URL must use HTTP or HTTPS")
    @Size(max = 2048)
    val avatarUrl: String?
)
