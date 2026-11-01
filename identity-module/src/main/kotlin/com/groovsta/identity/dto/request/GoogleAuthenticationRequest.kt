package com.groovsta.identity.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class GoogleAuthenticationRequest(
    @NotBlank
    @Size(max = 8192)
    val idToken: String
)
