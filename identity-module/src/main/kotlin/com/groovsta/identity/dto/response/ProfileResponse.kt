package com.groovsta.identity.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

data class ProfileResponse(
    val accountId: UUID,
    val handle: String,
    val displayName: String,
    val bio: String?,
    val avatarUrl: String?,
    val isPublic: Boolean,
    val followerCount: Long,
    val followingCount: Long,
    val postCount: Long,
    val createdAt: Instant,
    @field:Schema(
        description = "State personalized for the authenticated viewer; null when Authorization is omitted.",
        nullable = true
    )
    val viewerState: ProfileViewerStateResponse? = null
)
