package com.groovsta.content.service.impl

import com.groovsta.content.dto.CreateContentReportRequest
import com.groovsta.content.dto.StoryReportResponse
import com.groovsta.content.repository.StoryRepository
import com.groovsta.content.service.StoryReportService
import com.groovsta.content.service.StoryService
import com.groovsta.shared.moderation.ContentReportGateway
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StoryReportServiceImpl(
    private val storyRepository: StoryRepository,
    private val storyService: StoryService,
    private val reportGateway: ContentReportGateway
) : StoryReportService {
    @Transactional
    override fun report(
        reporterAccountId: UUID,
        storyId: UUID,
        request: CreateContentReportRequest
    ): StoryReportResponse {
        storyService.ensureVisible(reporterAccountId, storyId)
        val story = storyRepository.getReferenceById(storyId)
        require(story.ownerAccountId != reporterAccountId) { "You cannot report your own story" }
        val reportId = reportGateway.reportStory(
            storyId,
            reporterAccountId,
            request.category,
            request.details?.trim()?.takeIf(String::isNotEmpty)
        )
        return StoryReportResponse(reportId, storyId, "PENDING")
    }
}
