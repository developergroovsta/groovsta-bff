package com.groovsta.identity.dto.response

import java.time.Instant
import java.util.UUID

data class ProfileResponse(
    val accountId: UUID,
    val handle: String,
    val displayName: String,
    val bio: String?,
    val avatarUrl: String?,
    val followerCount: Long,
    val followingCount: Long,
    val createdAt: Instant
)
