package com.groovsta.content.dto

import com.groovsta.shared.moderation.ReportCategory
import jakarta.validation.constraints.Size

data class CreateContentReportRequest(
    val category: ReportCategory,
    @Size(max = 1000)
    val details: String?
)
