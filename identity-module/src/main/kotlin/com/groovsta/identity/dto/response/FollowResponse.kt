package com.groovsta.identity.dto.response

import java.util.UUID

data class FollowResponse(
    val accountId: UUID,
    val following: Boolean
)
