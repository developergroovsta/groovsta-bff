package com.groovsta.trustadmin.service

import com.groovsta.trustadmin.dto.ReportDecisionRequest
import com.groovsta.trustadmin.dto.ContentReportAdminResponse
import java.util.UUID

interface ModerationService {
    fun pendingReports(secret: String, limit: Int): List<ContentReportAdminResponse>
    fun decideReport(secret: String, reportId: UUID, request: ReportDecisionRequest): ContentReportAdminResponse
}
