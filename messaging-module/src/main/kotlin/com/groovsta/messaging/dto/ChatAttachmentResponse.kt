package com.groovsta.messaging.dto

import com.groovsta.messaging.model.ChatAttachmentMediaType
import com.groovsta.messaging.model.ChatAttachmentStatus
import java.util.UUID

data class ChatAttachmentResponse(
    val id: UUID,
    val mediaType: ChatAttachmentMediaType,
    val status: ChatAttachmentStatus,
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val width: Int,
    val height: Int,
    val durationMillis: Long?,
    val contentType: String,
    val sizeBytes: Long
)
