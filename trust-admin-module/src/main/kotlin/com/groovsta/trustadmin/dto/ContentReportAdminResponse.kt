package com.groovsta.trustadmin.dto

import com.groovsta.shared.moderation.ReportCategory
import java.time.Instant
import java.util.UUID

data class ContentReportAdminResponse(
    val id: UUID,
    val contentId: UUID?,
    val storyId: UUID?,
    val reporterAccountId: UUID,
    val category: ReportCategory,
    val details: String?,
    val status: String,
    val resolution: String,
    val moderatorReason: String?,
    val createdAt: Instant,
    val reviewedAt: Instant?
)
