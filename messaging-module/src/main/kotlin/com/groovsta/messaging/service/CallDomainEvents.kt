package com.groovsta.messaging.service

import com.groovsta.messaging.dto.CallEventResponse
import java.util.UUID

data class CallCommittedEvent(
    val eventByAccountId: Map<UUID, CallEventResponse>
)
