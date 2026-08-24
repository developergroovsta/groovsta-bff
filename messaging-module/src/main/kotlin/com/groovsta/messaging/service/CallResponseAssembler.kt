package com.groovsta.messaging.service

import com.groovsta.identity.service.ProfileService
import com.groovsta.messaging.dto.CallParticipantResponse
import com.groovsta.messaging.dto.CallResponse
import com.groovsta.messaging.dto.CallViewerStateResponse
import com.groovsta.messaging.model.CallParticipantRole
import com.groovsta.messaging.model.CallParticipantStatus
import com.groovsta.messaging.model.CallStatus
import com.groovsta.messaging.model.ChatCall
import com.groovsta.messaging.model.ChatCallParticipant
import com.groovsta.messaging.repository.ChatCallParticipantRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CallResponseAssembler(
    private val participantRepository: ChatCallParticipantRepository,
    private val profileService: ProfileService
) {
    fun response(call: ChatCall, viewerAccountId: UUID): CallResponse =
        requireNotNull(responses(listOf(call), viewerAccountId)[call.id])

    fun responses(calls: Collection<ChatCall>, viewerAccountId: UUID): Map<UUID, CallResponse> {
        if (calls.isEmpty()) return emptyMap()
        val participantsByCall = participantRepository.findByCallIdIn(calls.map(ChatCall::id))
            .groupBy(ChatCallParticipant::callId)
        val accountIds = participantsByCall.values.flatten().flatMapTo(mutableSetOf()) {
            listOf(it.accountId, it.invitedByAccountId)
        }
        val profiles = profileService.summaries(accountIds)
        return calls.associate { call ->
            call.id to build(call, viewerAccountId, participantsByCall[call.id].orEmpty(), profiles)
        }
    }

    fun responsesForMembers(call: ChatCall): Map<UUID, CallResponse> {
        val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        val profiles = profileService.summaries(
            participants.flatMapTo(mutableSetOf()) { listOf(it.accountId, it.invitedByAccountId) }
        )
        return participants.associate { participant ->
            participant.accountId to build(call, participant.accountId, participants, profiles)
        }
    }

    private fun build(
        call: ChatCall,
        viewerAccountId: UUID,
        participants: Collection<ChatCallParticipant>,
        profiles: Map<UUID, com.groovsta.identity.dto.response.ProfileSummaryResponse>
    ): CallResponse {
        val orderedParticipants = participants.sortedWith(
            compareBy<ChatCallParticipant> { it.invitedAt }.thenBy { it.id }
        )
        val viewer = orderedParticipants.firstOrNull { it.accountId == viewerAccountId }
            ?: throw IllegalArgumentException("Account is not a call participant")
        val ongoing = call.status != CallStatus.ENDED
        val isHost = viewer.role == CallParticipantRole.HOST
        return CallResponse(
            id = call.id,
            clientCallId = call.clientCallId,
            type = call.type,
            status = call.status,
            endReason = call.endReason,
            initiator = requireNotNull(profiles[call.initiatorAccountId]) {
                "Call initiator profile was not found"
            },
            participants = orderedParticipants.map { participant ->
                CallParticipantResponse(
                    id = participant.id,
                    account = requireNotNull(profiles[participant.accountId]) {
                        "Call participant profile was not found"
                    },
                    invitedBy = requireNotNull(profiles[participant.invitedByAccountId]) {
                        "Call inviter profile was not found"
                    },
                    role = participant.role,
                    status = participant.status,
                    microphoneMuted = participant.microphoneMuted,
                    cameraEnabled = participant.cameraEnabled,
                    invitedAt = participant.invitedAt,
                    joinedAt = participant.joinedAt,
                    leftAt = participant.leftAt
                )
            },
            groupCall = orderedParticipants.size > 2,
            maxParticipants = call.maxParticipants,
            ringExpiresAt = call.ringExpiresAt,
            startedAt = call.startedAt,
            endedAt = call.endedAt,
            createdAt = call.createdAt,
            viewerState = CallViewerStateResponse(
                participantStatus = viewer.status,
                canAccept = ongoing && viewer.status == CallParticipantStatus.INVITED,
                canDecline = ongoing && viewer.status == CallParticipantStatus.INVITED,
                canCancel = call.status == CallStatus.RINGING && call.initiatorAccountId == viewerAccountId,
                canEnd = call.status == CallStatus.ACTIVE && isHost && viewer.status == CallParticipantStatus.JOINED,
                canLeave = call.status == CallStatus.ACTIVE && viewer.status == CallParticipantStatus.JOINED,
                canInvite = ongoing && isHost && viewer.status == CallParticipantStatus.JOINED &&
                    orderedParticipants.size < call.maxParticipants
            )
        )
    }
}
