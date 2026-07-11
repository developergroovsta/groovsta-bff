package com.groovsta.identity.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class VerifyCodeRequest(
    @NotBlank
    @Pattern(regexp = "\\d{6}")
    val code: String
)
