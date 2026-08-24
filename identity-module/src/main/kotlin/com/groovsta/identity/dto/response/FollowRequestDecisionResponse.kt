package com.groovsta.identity.dto.response

import com.groovsta.identity.model.FollowStatus
import java.util.UUID

data class FollowRequestDecisionResponse(
    val requestId: UUID,
    val requesterAccountId: UUID,
    val status: FollowStatus
)
