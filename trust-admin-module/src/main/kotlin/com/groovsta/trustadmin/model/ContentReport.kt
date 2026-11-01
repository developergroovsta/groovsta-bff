package com.groovsta.trustadmin.model

import com.groovsta.shared.moderation.ReportCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "trust_content_reports",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["content_id", "reporter_account_id"]),
        UniqueConstraint(columnNames = ["story_id", "reporter_account_id"])
    ]
)
class ContentReport(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "content_id") var contentId: UUID? = null,
    @Column(name = "story_id") var storyId: UUID? = null,
    @Column(name = "reporter_account_id", nullable = false) var reporterAccountId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) var category: ReportCategory = ReportCategory.OTHER,
    @Column(length = 1000) var details: String? = null,
    @Column(nullable = false, length = 20) var status: String = "PENDING",
    @Column(nullable = false, length = 20) var resolution: String = "NONE",
    @Column(name = "moderator_reason", length = 1000) var moderatorReason: String? = null,
    @Column(name = "reviewed_at") var reviewedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
