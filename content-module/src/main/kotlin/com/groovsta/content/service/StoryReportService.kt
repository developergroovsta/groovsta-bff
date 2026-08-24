package com.groovsta.content.service

import com.groovsta.content.dto.CreateContentReportRequest
import com.groovsta.content.dto.StoryReportResponse
import java.util.UUID

interface StoryReportService {
    fun report(
        reporterAccountId: UUID,
        storyId: UUID,
        request: CreateContentReportRequest
    ): StoryReportResponse
}
