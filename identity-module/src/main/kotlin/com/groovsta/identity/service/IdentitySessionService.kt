package com.groovsta.identity.service

import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.dto.response.SessionResponse
import java.util.UUID

interface IdentitySessionService {
    fun create(accountId: UUID, device: DeviceMetadata): SessionResponse
    fun hash(accessToken: String): String
}
