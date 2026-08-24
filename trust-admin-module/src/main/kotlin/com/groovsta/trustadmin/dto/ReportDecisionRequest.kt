package com.groovsta.trustadmin.dto

import jakarta.validation.constraints.Size

data class ReportDecisionRequest(
    val decision: ReportDecision,
    @Size(max = 1000) val reason: String?
)
