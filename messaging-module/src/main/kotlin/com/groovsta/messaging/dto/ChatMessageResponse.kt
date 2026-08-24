package com.groovsta.messaging.dto

import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.messaging.model.ChatMessageType
import java.time.Instant
import java.util.UUID

data class ChatMessageResponse(
    val id: UUID,
    val conversationId: UUID,
    val sender: ProfileSummaryResponse,
    val type: ChatMessageType,
    val text: String?,
    val sharedContent: SharedContentPreviewResponse?,
    val attachments: List<ChatAttachmentResponse>,
    val replyTo: ChatReplyPreviewResponse?,
    val reactions: List<ChatReactionSummaryResponse>,
    val deliveryStatus: ChatDeliveryStatus,
    val isRead: Boolean,
    val sentAt: Instant,
    val editedAt: Instant?,
    val unsent: Boolean,
    val unsentAt: Instant?
)
