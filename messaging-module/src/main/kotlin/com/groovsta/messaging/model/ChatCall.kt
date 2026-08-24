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
    name = "chat_calls",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_chat_call_initiator_client_id",
            columnNames = ["initiator_account_id", "client_call_id"]
        )
    ]
)
class ChatCall(
    id: UUID = UUID.randomUUID(),
    @Column(name = "initiator_account_id", nullable = false)
    var initiatorAccountId: UUID = UUID.randomUUID(),
    @Column(name = "client_call_id", nullable = false)
    var clientCallId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false, length = 10)
    var type: CallType = CallType.AUDIO,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    var status: CallStatus = CallStatus.RINGING,
    @Column(name = "max_participants", nullable = false)
    var maxParticipants: Int = 8,
    @Column(name = "ring_expires_at", nullable = false)
    var ringExpiresAt: Instant = Instant.EPOCH,
    @Column(name = "started_at")
    var startedAt: Instant? = null,
    @Column(name = "ended_at")
    var endedAt: Instant? = null,
    @Column(name = "ended_by_account_id")
    var endedByAccountId: UUID? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 12)
    var endReason: CallEndReason? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : MessagingModel(id, createdAt, updatedAt)
