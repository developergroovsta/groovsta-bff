package com.groovsta.identity.service

import com.groovsta.identity.dto.response.CloseFriendsResponse
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.util.UUID

interface CloseFriendService {
    fun list(ownerAccountId: UUID): CloseFriendsResponse
    fun add(ownerAccountId: UUID, friendHandle: String): ProfileSummaryResponse
    fun remove(ownerAccountId: UUID, friendHandle: String)
}
