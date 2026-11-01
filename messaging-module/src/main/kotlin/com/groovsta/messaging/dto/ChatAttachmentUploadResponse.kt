package com.groovsta.messaging.dto

import com.groovsta.messaging.model.ChatAttachmentUploadStatus
import java.time.Instant
import java.util.UUID

data class ChatAttachmentUploadResponse(
    val uploadBatchId: UUID,
    val clientMessageId: UUID,
    val status: ChatAttachmentUploadStatus,
    val messageId: UUID?,
    val uploads: List<ChatAttachmentUploadItemResponse>,
    val expiresAt: Instant
)
