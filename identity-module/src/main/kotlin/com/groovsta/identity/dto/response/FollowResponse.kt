package com.groovsta.identity.dto.response

import java.util.UUID

data class FollowResponse(
    val accountId: UUID,
    val relationship: ProfileRelationship,
    val following: Boolean,
    val followerCount: Long
)
