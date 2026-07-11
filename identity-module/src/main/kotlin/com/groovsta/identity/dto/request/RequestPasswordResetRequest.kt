package com.groovsta.identity.dto.request

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class RequestPasswordResetRequest(
    @Email
    @Size(max = 320)
    val email: String? = null,
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164")
    val phoneNumber: String? = null
) {
    @AssertTrue(message = "Provide either email or phone number, not both")
    fun hasExactlyOneContactMethod(): Boolean = listOf(email, phoneNumber).count { !it.isNullOrBlank() } == 1
}
