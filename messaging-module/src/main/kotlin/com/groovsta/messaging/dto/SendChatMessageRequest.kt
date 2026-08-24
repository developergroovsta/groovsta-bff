package com.groovsta.messaging.dto

import com.groovsta.messaging.model.ChatMessageType
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Size
import java.util.UUID

data class SendChatMessageRequest(
    val clientMessageId: UUID,
    val recipientAccountId: UUID,
    val type: ChatMessageType,
    @Size(max = 2000)
    val text: String? = null,
    val sharedContentId: UUID? = null,
    val replyToMessageId: UUID? = null
) {
    @AssertTrue(message = "TEXT requires text and cannot include sharedContentId")
    fun validTextPayload(): Boolean =
        type != ChatMessageType.TEXT || (!text.isNullOrBlank() && sharedContentId == null)

    @AssertTrue(message = "CONTENT_SHARE requires sharedContentId")
    fun validContentSharePayload(): Boolean =
        type != ChatMessageType.CONTENT_SHARE || sharedContentId != null

    @AssertTrue(message = "MEDIA messages must use the chat attachment upload endpoints")
    fun mediaUsesUploadFlow(): Boolean = type != ChatMessageType.MEDIA
}
