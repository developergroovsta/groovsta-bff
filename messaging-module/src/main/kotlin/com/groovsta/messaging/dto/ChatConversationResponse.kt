package com.groovsta.messaging.dto

import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.time.Instant
import java.util.UUID

data class ChatConversationResponse(
    val id: UUID,
    val otherUser: ProfileSummaryResponse,
    val otherUserOnline: Boolean,
    val lastMessage: ChatMessageResponse,
    val hasUnread: Boolean,
    val updatedAt: Instant
)
