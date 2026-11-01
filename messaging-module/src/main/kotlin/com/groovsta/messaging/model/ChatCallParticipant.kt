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
    name = "chat_call_participants",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_chat_call_participant",
            columnNames = ["call_id", "account_id"]
        )
    ]
)
class ChatCallParticipant(
    id: UUID = UUID.randomUUID(),
    @Column(name = "call_id", nullable = false)
    var callId: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(name = "initial_participant", nullable = false)
    var initialParticipant: Boolean = true,
    @Column(name = "invited_by_account_id", nullable = false)
    var invitedByAccountId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var role: CallParticipantRole = CallParticipantRole.MEMBER,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    var status: CallParticipantStatus = CallParticipantStatus.INVITED,
    @Column(name = "invited_at", nullable = false)
    var invitedAt: Instant = Instant.EPOCH,
    @Column(name = "joined_at")
    var joinedAt: Instant? = null,
    @Column(name = "left_at")
    var leftAt: Instant? = null,
    @Column(name = "last_heartbeat_at")
    var lastHeartbeatAt: Instant? = null,
    @Column(name = "microphone_muted", nullable = false)
    var microphoneMuted: Boolean = false,
    @Column(name = "camera_enabled", nullable = false)
    var cameraEnabled: Boolean = false,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : MessagingModel(id, createdAt, updatedAt)
