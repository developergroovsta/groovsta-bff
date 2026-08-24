package com.groovsta.content.controller

import com.groovsta.content.dto.CreateContentReportRequest
import com.groovsta.content.service.StoryReportService
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stories/{storyId}/reports")
@Tag(name = "Story Reports")
class StoryReportController(
    private val service: StoryReportService,
    private val resolver: AuthenticatedAccountResolver
) {
    @PostMapping
    @Operation(
        summary = "Report an active visible story",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun report(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable storyId: UUID,
        @Valid @RequestBody request: CreateContentReportRequest
    ) = service.report(resolver.resolve(authorization), storyId, request)
}
