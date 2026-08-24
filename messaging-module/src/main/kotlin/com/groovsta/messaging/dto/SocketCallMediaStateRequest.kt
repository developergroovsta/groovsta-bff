package com.groovsta.messaging.dto

import java.util.UUID

data class SocketCallMediaStateRequest(
    val callId: UUID,
    val microphoneMuted: Boolean,
    val cameraEnabled: Boolean
)
