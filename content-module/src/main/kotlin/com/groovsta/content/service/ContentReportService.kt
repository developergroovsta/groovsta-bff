package com.groovsta.content.service

import com.groovsta.content.dto.ContentReportResponse
import com.groovsta.content.dto.CreateContentReportRequest
import java.util.UUID

interface ContentReportService {
    fun report(reporterAccountId: UUID, contentId: UUID, request: CreateContentReportRequest): ContentReportResponse
}
