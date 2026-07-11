package com.groovsta.identity.service

import com.groovsta.identity.dto.request.UpdateProfileRequest
import com.groovsta.identity.dto.response.ProfileResponse
import java.util.UUID

interface ProfileService {
    fun getByAccountId(accountId: UUID): ProfileResponse
    fun getByHandle(handle: String): ProfileResponse
    fun update(accountId: UUID, request: UpdateProfileRequest): ProfileResponse
}
