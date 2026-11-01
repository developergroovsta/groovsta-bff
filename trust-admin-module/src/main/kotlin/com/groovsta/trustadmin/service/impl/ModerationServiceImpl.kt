package com.groovsta.trustadmin.service.impl

import com.groovsta.shared.moderation.ContentReportGateway
import com.groovsta.shared.moderation.ContentTakedownPort
import com.groovsta.shared.moderation.StoryTakedownPort
import com.groovsta.shared.moderation.ReportCategory
import com.groovsta.trustadmin.config.TrustProperties
import com.groovsta.trustadmin.dto.ReportDecision
import com.groovsta.trustadmin.dto.ReportDecisionRequest
import com.groovsta.trustadmin.dto.ContentReportAdminResponse
import com.groovsta.trustadmin.model.ContentReport
import com.groovsta.trustadmin.repository.ContentReportRepository
import com.groovsta.trustadmin.service.ModerationService
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class ModerationServiceImpl(
    private val repository: ContentReportRepository,
    private val takedownPort: ContentTakedownPort,
    private val storyTakedownPort: StoryTakedownPort,
    private val properties: TrustProperties
) : ModerationService, ContentReportGateway {

    @Transactional
    override fun report(contentId: UUID, reporterAccountId: UUID, category: ReportCategory, details: String?): UUID {
        val existing = repository.findByContentIdAndReporterAccountId(contentId, reporterAccountId)
        if (existing != null) return existing.id
        return repository.save(ContentReport(
            contentId = contentId,
            reporterAccountId = reporterAccountId,
            category = category,
            details = details
        )).id
    }

    @Transactional
    override fun reportStory(
        storyId: UUID,
        reporterAccountId: UUID,
        category: ReportCategory,
        details: String?
    ): UUID {
        val existing = repository.findByStoryIdAndReporterAccountId(storyId, reporterAccountId)
        if (existing != null) return existing.id
        return repository.save(
            ContentReport(
                storyId = storyId,
                reporterAccountId = reporterAccountId,
                category = category,
                details = details
            )
        ).id
    }

    @Transactional(readOnly = true)
    override fun pendingReports(secret: String, limit: Int): List<ContentReportAdminResponse> {
        authorize(secret)
        return repository.findByStatusOrderByCreatedAt("PENDING", PageRequest.of(0, limit.coerceIn(1, 100))).map(::response)
    }

    @Transactional
    override fun decideReport(secret: String, reportId: UUID, request: ReportDecisionRequest): ContentReportAdminResponse {
        authorize(secret)
        val report = repository.findById(reportId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Content report was not found")
        }
        require(report.status == "PENDING") { "Content report is already resolved" }
        val now = Instant.now()
        val reason = request.reason?.trim()?.takeIf(String::isNotEmpty)

        if (request.decision == ReportDecision.TAKE_DOWN) {
            require(!reason.isNullOrBlank()) { "A takedown reason is required" }
            reportsForTakedown(report, reason).forEach {
                resolve(it, ReportDecision.TAKE_DOWN, reason, now)
            }
        } else {
            resolve(report, ReportDecision.DISMISS, reason, now)
        }
        return response(report)
    }

    private fun reportsForTakedown(report: ContentReport, reason: String): List<ContentReport> =
        report.contentId?.let { contentId ->
            takedownPort.takeDown(contentId, reason)
            repository.findAllByContentIdAndStatus(contentId, "PENDING")
        } ?: requireNotNull(report.storyId).let { storyId ->
            storyTakedownPort.takeDownStory(storyId, reason)
            repository.findAllByStoryIdAndStatus(storyId, "PENDING")
        }

    private fun resolve(report: ContentReport, decision: ReportDecision, reason: String?, now: Instant) {
        report.status = "RESOLVED"
        report.resolution = decision.name
        report.moderatorReason = reason
        report.reviewedAt = now
        report.updatedAt = now
    }

    private fun authorize(secret: String) {
        if (secret != properties.moderationAdminKey) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid moderation admin key")
        }
    }

    private fun response(report: ContentReport) = ContentReportAdminResponse(
        report.id, report.contentId, report.storyId, report.reporterAccountId, report.category, report.details,
        report.status, report.resolution, report.moderatorReason, report.createdAt, report.reviewedAt
    )
}
