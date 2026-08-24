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
    name = "chat_attachment_upload_batches",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_chat_attachment_batch_client_message",
            columnNames = ["sender_account_id", "client_message_id"]
        )
    ]
)
class ChatAttachmentUploadBatch(
    id: UUID = UUID.randomUUID(),
    @Column(name = "sender_account_id", nullable = false)
    var senderAccountId: UUID = UUID.randomUUID(),
    @Column(name = "recipient_account_id", nullable = false)
    var recipientAccountId: UUID = UUID.randomUUID(),
    @Column(name = "client_message_id", nullable = false)
    var clientMessageId: UUID = UUID.randomUUID(),
    @Column(name = "message_id", unique = true)
    var messageId: UUID? = null,
    @Column(name = "message_text", length = 2000)
    var text: String? = null,
    @Column(name = "reply_to_message_id")
    var replyToMessageId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ChatAttachmentUploadStatus = ChatAttachmentUploadStatus.PENDING,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.EPOCH,
    @Column(name = "completed_at")
    var completedAt: Instant? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : MessagingModel(id, createdAt, updatedAt)
