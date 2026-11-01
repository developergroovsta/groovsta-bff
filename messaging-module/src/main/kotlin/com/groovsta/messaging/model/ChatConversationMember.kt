package com.groovsta.messaging.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "chat_conversation_members",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_chat_conversation_member",
            columnNames = ["conversation_id", "account_id"]
        )
    ]
)
class ChatConversationMember(
    id: UUID = UUID.randomUUID(),
    @Column(name = "conversation_id", nullable = false)
    var conversationId: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(name = "last_read_message_id")
    var lastReadMessageId: UUID? = null,
    @Column(name = "last_read_at")
    var lastReadAt: Instant? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : MessagingModel(id, createdAt, updatedAt)
