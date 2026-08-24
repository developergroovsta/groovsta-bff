package com.groovsta.messaging.service

import com.groovsta.messaging.dto.ChatEventResponse
import java.util.UUID

data class ChatCommittedEvent(
    val participantAccountIds: Set<UUID>,
    val event: ChatEventResponse,
    val eventByAccountId: Map<UUID, ChatEventResponse> = emptyMap()
)
