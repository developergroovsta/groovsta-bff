package com.groovsta.identity.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CompleteGoogleSignupRequest(
    @NotBlank
    @Size(max = 128)
    val registrationToken: String,
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_]{3,30}")
    val handle: String,
    @NotBlank
    @Size(max = 80)
    val displayName: String,
    @AssertTrue(message = "An 18+ declaration is required")
    val adultDeclaration: Boolean
)
