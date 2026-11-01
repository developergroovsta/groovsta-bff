package com.groovsta.messaging.dto

import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.messaging.model.ChatMessageType
import java.util.UUID

data class ChatReplyPreviewResponse(
    val id: UUID,
    val sender: ProfileSummaryResponse,
    val type: ChatMessageType,
    val text: String?,
    val sharedContent: SharedContentPreviewResponse?,
    val attachments: List<ChatAttachmentResponse>,
    val unsent: Boolean
)
