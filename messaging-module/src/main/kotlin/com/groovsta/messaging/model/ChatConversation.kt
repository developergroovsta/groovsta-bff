package com.groovsta.messaging.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "chat_conversations",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_chat_conversation_participants",
            columnNames = ["participant_one_account_id", "participant_two_account_id"]
        )
    ]
)
class ChatConversation(
    id: UUID = UUID.randomUUID(),
    @Column(name = "participant_one_account_id", nullable = false)
    var participantOneAccountId: UUID = UUID.randomUUID(),
    @Column(name = "participant_two_account_id", nullable = false)
    var participantTwoAccountId: UUID = UUID.randomUUID(),
    @Column(name = "last_message_id")
    var lastMessageId: UUID? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : MessagingModel(id, createdAt, updatedAt) {

    fun contains(accountId: UUID): Boolean =
        participantOneAccountId == accountId || participantTwoAccountId == accountId

    fun otherParticipant(accountId: UUID): UUID = when (accountId) {
        participantOneAccountId -> participantTwoAccountId
        participantTwoAccountId -> participantOneAccountId
        else -> throw IllegalArgumentException("Account is not a conversation participant")
    }
}
