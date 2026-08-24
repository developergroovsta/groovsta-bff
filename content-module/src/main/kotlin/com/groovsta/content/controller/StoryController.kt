package com.groovsta.content.controller

import com.groovsta.content.dto.CompleteStoryUploadRequest
import com.groovsta.content.dto.CreateStoryRequest
import com.groovsta.content.service.StoryService
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stories")
@Tag(name = "Stories")
class StoryController(
    private val service: StoryService,
    private val resolver: AuthenticatedAccountResolver
) {
    @PostMapping
    @Operation(
        summary = "Create a story upload or share an available post, reel, or story",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun create(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @Valid @RequestBody request: CreateStoryRequest
    ) = service.create(resolver.resolve(auth), request)

    @PostMapping("/{storyId}/uploads/complete")
    @Operation(
        summary = "Complete a story media upload and start its 24-hour lifetime",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun completeUpload(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID,
        @Valid @RequestBody request: CompleteStoryUploadRequest
    ) = service.completeUpload(resolver.resolve(auth), storyId, request)

    @GetMapping("/{storyId}")
    @Operation(
        summary = "Get an active story when visible to the viewer",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun get(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable storyId: UUID
    ) = personalizedResponse(service.get(optionalAccountId(auth), storyId))

    @PostMapping("/{storyId}/views")
    @Operation(
        summary = "Mark an active visible story as seen",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun markSeen(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID
    ) = service.markSeen(resolver.resolve(auth), storyId)

    @GetMapping("/{storyId}/views")
    @Operation(
        summary = "List users who viewed a story; available only to the story owner",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun viewers(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalizedResponse(service.viewers(resolver.resolve(auth), storyId, cursor, limit))

    @DeleteMapping("/{storyId}")
    @Operation(
        summary = "Delete one of the current account's stories",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun delete(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID
    ): ResponseEntity<Void> {
        service.delete(resolver.resolve(auth), storyId)
        return ResponseEntity.noContent().build()
    }

    private fun optionalAccountId(auth: String?): UUID? =
        auth?.takeIf(String::isNotBlank)?.let(resolver::resolve)

    private fun <T> personalizedResponse(body: T): ResponseEntity<T> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
        .body(body)
}
