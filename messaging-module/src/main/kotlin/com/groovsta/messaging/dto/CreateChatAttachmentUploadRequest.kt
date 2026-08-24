package com.groovsta.messaging.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateChatAttachmentUploadRequest(
    val clientMessageId: UUID,
    val recipientAccountId: UUID,
    @Size(max = 2000)
    val text: String? = null,
    val replyToMessageId: UUID? = null,
    @Valid
    @Size(min = 1, max = 10)
    val attachments: List<CreateChatAttachmentRequest>
)
