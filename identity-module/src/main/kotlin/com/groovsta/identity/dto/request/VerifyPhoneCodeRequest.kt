package com.groovsta.identity.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class VerifyPhoneCodeRequest(
    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164")
    val phoneNumber: String,
    @NotBlank
    @Pattern(regexp = "\\d{6}")
    val code: String
)
