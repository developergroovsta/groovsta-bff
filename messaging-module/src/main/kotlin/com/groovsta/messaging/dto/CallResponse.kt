package com.groovsta.messaging.dto

import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.messaging.model.CallEndReason
import com.groovsta.messaging.model.CallStatus
import com.groovsta.messaging.model.CallType
import java.time.Instant
import java.util.UUID

data class CallResponse(
    val id: UUID,
    val clientCallId: UUID,
    val type: CallType,
    val status: CallStatus,
    val endReason: CallEndReason?,
    val initiator: ProfileSummaryResponse,
    val participants: List<CallParticipantResponse>,
    val groupCall: Boolean,
    val maxParticipants: Int,
    val ringExpiresAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val createdAt: Instant,
    val viewerState: CallViewerStateResponse
)
