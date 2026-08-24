package com.groovsta.messaging.dto

import com.groovsta.messaging.model.CallType
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateCallRequest(
    val clientCallId: UUID,
    val type: CallType,
    @Size(min = 1, max = 7)
    val participantAccountIds: List<UUID>
)
