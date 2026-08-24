package com.groovsta.content.dto

import java.util.UUID

data class StoryReportResponse(
    val reportId: UUID,
    val storyId: UUID,
    val status: String
)
