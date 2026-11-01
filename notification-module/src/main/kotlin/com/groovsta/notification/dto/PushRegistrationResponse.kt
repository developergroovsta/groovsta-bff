package com.groovsta.notification.dto

import com.groovsta.identity.model.DevicePlatform
import com.groovsta.notification.model.PushIdentifierType
import java.time.Instant
import java.util.UUID

data class PushRegistrationResponse(
    val id: UUID,
    val deviceIdentifier: String,
    val platform: DevicePlatform,
    val identifierType: PushIdentifierType,
    val active: Boolean,
    val registeredAt: Instant
)
