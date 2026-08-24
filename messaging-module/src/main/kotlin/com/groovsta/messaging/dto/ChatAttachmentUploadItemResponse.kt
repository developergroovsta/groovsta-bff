package com.groovsta.messaging.dto

import com.groovsta.messaging.model.ChatAttachmentMediaType
import java.time.Instant
import java.util.UUID

data class ChatAttachmentUploadItemResponse(
    val attachmentId: UUID,
    val position: Int,
    val mediaType: ChatAttachmentMediaType,
    val uploadUrl: String?,
    val requiredHeaders: Map<String, String>,
    val expiresAt: Instant?
)
