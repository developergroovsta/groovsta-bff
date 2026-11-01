package com.groovsta.notification.service

import com.groovsta.notification.dto.PushRegistrationResponse
import com.groovsta.notification.dto.RegisterPushTokenRequest
import java.util.UUID

interface PushRegistrationService {
    fun register(accountId: UUID, deviceIdentifier: String, request: RegisterPushTokenRequest): PushRegistrationResponse
    fun unregister(accountId: UUID, deviceIdentifier: String)
}
