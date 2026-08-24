package com.groovsta.content.controller

import com.groovsta.content.dto.CompleteContentUploadRequest
import com.groovsta.content.dto.CreateContentUploadRequest
import com.groovsta.content.service.ContentService
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/content")
@Tag(name = "Content")
class ContentController(
    private val service: ContentService,
    private val resolver: AuthenticatedAccountResolver
) {
    @PostMapping("/uploads")
    @Operation(
        summary = "Create S3 uploads for an image post or reel",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun createUpload(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @Valid @RequestBody request: CreateContentUploadRequest
    ) = service.createUpload(resolver.resolve(auth), request)

    @PostMapping("/{contentId}/uploads/complete")
    @Operation(
        summary = "Complete an image post or reel upload",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun completeUpload(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable contentId: UUID,
        @Valid @RequestBody request: CompleteContentUploadRequest
    ) = service.completeUpload(resolver.resolve(auth), contentId, request)

    @GetMapping("/{contentId}")
    @Operation(summary = "Get a published content item")
    fun get(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable contentId: UUID
    ) = service.get(auth?.let(resolver::resolve), contentId)

    @PostMapping("/{contentId}/publish")
    @Operation(
        summary = "Publish a successfully transcoded reel",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun publish(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable contentId: UUID
    ) = service.publish(resolver.resolve(auth), contentId)
}
