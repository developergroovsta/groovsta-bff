package com.groovsta.messaging.dto

import jakarta.validation.constraints.Size
import java.util.UUID

data class CallSignalRequest(
    val callId: UUID,
    val recipientAccountId: UUID,
    val type: CallSignalType,
    @Size(max = 100000)
    val sdp: String? = null,
    @Size(max = 4096)
    val candidate: String? = null,
    @Size(max = 256)
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)
