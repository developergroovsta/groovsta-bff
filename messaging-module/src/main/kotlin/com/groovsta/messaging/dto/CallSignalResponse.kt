package com.groovsta.messaging.dto

import java.util.UUID

data class CallSignalResponse(
    val callId: UUID,
    val senderAccountId: UUID,
    val recipientAccountId: UUID,
    val type: CallSignalType,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)
