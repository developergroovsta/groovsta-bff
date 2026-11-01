package com.groovsta.identity.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(
    @NotBlank
    @Size(max = 80)
    val displayName: String,
    @Size(max = 500)
    val bio: String?,
    val isPublic: Boolean? = null
)
