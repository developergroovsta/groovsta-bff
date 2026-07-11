package com.groovsta.identity.dto.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class SendEmailVerificationRequest(
    @Email
    @Size(max = 320)
    val email: String? = null
)
