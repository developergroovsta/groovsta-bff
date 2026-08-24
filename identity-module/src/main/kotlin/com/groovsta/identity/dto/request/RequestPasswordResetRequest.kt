package com.groovsta.identity.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Provide exactly one of email or phoneNumber")
data class RequestPasswordResetRequest(
    @Email
    @Size(max = 320)
    @Schema(example = "rajat@example.com", description = "Verified account email; omit when using phoneNumber")
    val email: String? = null,
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164")
    @Schema(example = "+919876543210", description = "Verified E.164 phone number; omit when using email")
    val phoneNumber: String? = null
) {
    @AssertTrue(message = "Provide either email or phone number, not both")
    fun hasExactlyOneContactMethod(): Boolean = listOf(email, phoneNumber).count { !it.isNullOrBlank() } == 1
}
