package com.groovsta.content.service.impl

import com.groovsta.content.dto.ContentReportResponse
import com.groovsta.content.dto.CreateContentReportRequest
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.service.ContentReportService
import com.groovsta.shared.moderation.ContentReportGateway
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ContentReportServiceImpl(
    private val contentRepository: ContentItemRepository,
    private val reportGateway: ContentReportGateway
) : ContentReportService {
    @Transactional
    override fun report(
        reporterAccountId: UUID,
        contentId: UUID,
        request: CreateContentReportRequest
    ): ContentReportResponse {
        val content = contentRepository.findById(contentId).orElseThrow { notFound() }
        if (content.status != ContentStatus.PUBLISHED) throw notFound()
        require(content.ownerAccountId != reporterAccountId) { "You cannot report your own content" }
        val reportId = reportGateway.report(
            contentId,
            reporterAccountId,
            request.category,
            request.details?.trim()?.takeIf(String::isNotEmpty)
        )
        return ContentReportResponse(reportId, contentId, "PENDING")
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Published content was not found")
}
