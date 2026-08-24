package com.groovsta.identity.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Schema(description = "Reset a password with the OTP sent to exactly one verified contact")
data class ConfirmPasswordResetRequest(
    @Email
    @Size(max = 320)
    @Schema(example = "rajat@example.com", description = "Email used to request the OTP; omit when using phoneNumber")
    val email: String? = null,
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164")
    @Schema(example = "+919876543210", description = "Phone number used to request the OTP; omit when using email")
    val phoneNumber: String? = null,
    @NotBlank
    @Pattern(regexp = "\\d{6}")
    @Schema(example = "123456", description = "Six-digit password-reset OTP")
    val code: String,
    @NotBlank
    @Size(min = 8, max = 128)
    @Schema(example = "NewSecurePassword123!", description = "New password, 8 to 128 characters")
    val newPassword: String
) {
    @AssertTrue(message = "Provide either email or phone number, not both")
    fun hasExactlyOneContactMethod(): Boolean = listOf(email, phoneNumber).count { !it.isNullOrBlank() } == 1
}
