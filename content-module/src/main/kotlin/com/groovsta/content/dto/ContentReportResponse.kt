package com.groovsta.content.dto

import java.util.UUID

data class ContentReportResponse(
    val reportId: UUID,
    val contentId: UUID,
    val status: String
)
