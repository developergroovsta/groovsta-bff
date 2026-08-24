package com.groovsta.messaging.dto

import com.groovsta.messaging.model.CallParticipantStatus

data class CallViewerStateResponse(
    val participantStatus: CallParticipantStatus,
    val canAccept: Boolean,
    val canDecline: Boolean,
    val canCancel: Boolean,
    val canEnd: Boolean,
    val canLeave: Boolean,
    val canInvite: Boolean
)
