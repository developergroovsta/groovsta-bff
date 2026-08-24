package com.groovsta.messaging.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "chat_message_reactions",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_chat_message_reaction_account",
            columnNames = ["message_id", "account_id"]
        )
    ]
)
class ChatMessageReaction(
    id: UUID = UUID.randomUUID(),
    @Column(name = "message_id", nullable = false)
    var messageId: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 32)
    var emoji: String = "",
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : MessagingModel(id, createdAt, updatedAt)
