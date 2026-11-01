package com.groovsta.identity.model

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
    name = "identity_content_mutes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["account_id", "muted_account_id", "content_type"])]
)
class ContentMute(
    id: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(name = "muted_account_id", nullable = false)
    var mutedAccountId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 20)
    var contentType: ContentMuteType = ContentMuteType.POST,
    createdAt: Instant = Instant.now(),
    updatedAt: Instant = Instant.now()
) : AuditableModel(id, createdAt, updatedAt)
