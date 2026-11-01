package com.groovsta.messaging.dto

import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.messaging.model.CallParticipantRole
import com.groovsta.messaging.model.CallParticipantStatus
import java.time.Instant
import java.util.UUID

data class CallParticipantResponse(
    val id: UUID,
    val account: ProfileSummaryResponse,
    val invitedBy: ProfileSummaryResponse,
    val role: CallParticipantRole,
    val status: CallParticipantStatus,
    val microphoneMuted: Boolean,
    val cameraEnabled: Boolean,
    val invitedAt: Instant,
    val joinedAt: Instant?,
    val leftAt: Instant?
)
