package com.groovsta.notification.dto

import com.groovsta.notification.model.PushIdentifierType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterPushTokenRequest(
    @NotBlank
    @Size(max = 4096)
    val registrationId: String,
    val identifierType: PushIdentifierType = PushIdentifierType.FCM_TOKEN
)
