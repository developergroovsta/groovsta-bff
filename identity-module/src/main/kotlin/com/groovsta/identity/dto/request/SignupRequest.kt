package com.groovsta.identity.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignupRequest(
    @Email
    @Size(max = 320)
    val email: String? = null,
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164")
    val phoneNumber: String? = null,
    @NotBlank
    @Size(min = 8, max = 128)
    val password: String,
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_]{3,30}")
    val handle: String,
    @NotBlank
    @Size(max = 80)
    val displayName: String,
    @AssertTrue(message = "An 18+ declaration is required")
    val adultDeclaration: Boolean
) {
    @AssertTrue(message = "Either email or phone number is required")
    fun hasContactMethod(): Boolean = !email.isNullOrBlank() || !phoneNumber.isNullOrBlank()
}
