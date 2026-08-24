package com.groovsta.messaging.dto

import com.groovsta.messaging.model.ChatAttachmentMediaType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateChatAttachmentRequest(
    val mediaType: ChatAttachmentMediaType,
    @NotBlank
    @Size(max = 255)
    val fileName: String,
    @NotBlank
    @Size(max = 100)
    val contentType: String,
    @Positive
    val sizeBytes: Long,
    @Positive
    val width: Int,
    @Positive
    val height: Int,
    @Positive
    val durationMillis: Long? = null
)
