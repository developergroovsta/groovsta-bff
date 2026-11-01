package com.groovsta.trustadmin.controller

import com.groovsta.trustadmin.dto.ReportDecisionRequest
import com.groovsta.trustadmin.service.ModerationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/moderation")
@Tag(name = "Moderation")
class ModerationController(private val service: ModerationService) {
    @GetMapping("/reports")
    @Operation(summary = "List pending user reports")
    fun pending(@RequestHeader("X-Moderation-Admin-Key") secret: String, @RequestParam(defaultValue = "20") limit: Int) =
        service.pendingReports(secret, limit)

    @PostMapping("/reports/{reportId}/decision")
    @Operation(summary = "Dismiss a report or take down its post, reel, or story")
    fun decide(@RequestHeader("X-Moderation-Admin-Key") secret: String, @PathVariable reportId: UUID, @Valid @RequestBody request: ReportDecisionRequest) =
        service.decideReport(secret, reportId, request)
}
