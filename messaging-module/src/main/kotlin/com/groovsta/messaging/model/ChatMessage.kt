package com.groovsta.messaging.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "chat_messages",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_chat_sender_client_message",
            columnNames = ["sender_account_id", "client_message_id"]
        )
    ]
)
class ChatMessage(
    id: UUID = UUID.randomUUID(),
    @Column(name = "conversation_id", nullable = false)
    var conversationId: UUID = UUID.randomUUID(),
    @Column(name = "sender_account_id", nullable = false)
    var senderAccountId: UUID = UUID.randomUUID(),
    @Column(name = "client_message_id", nullable = false)
    var clientMessageId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    var type: ChatMessageType = ChatMessageType.TEXT,
    @Column(name = "message_text", length = 2000)
    var text: String? = null,
    @Column(name = "shared_content_id")
    var sharedContentId: UUID? = null,
    @Column(name = "reply_to_message_id")
    var replyToMessageId: UUID? = null,
    @Column(name = "edited_at")
    var editedAt: Instant? = null,
    @Column(name = "unsent_at")
    var unsentAt: Instant? = null,
    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : MessagingModel(id, createdAt, updatedAt)
