package com.groovsta.messaging.dto

import jakarta.validation.constraints.Size
import java.util.UUID

data class AddCallParticipantsRequest(
    @Size(min = 1, max = 7)
    val participantAccountIds: List<UUID>
)
