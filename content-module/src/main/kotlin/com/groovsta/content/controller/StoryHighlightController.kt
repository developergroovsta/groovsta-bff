package com.groovsta.content.controller

import com.groovsta.content.dto.UpsertStoryHighlightRequest
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Story Archive and Highlights")
class StoryHighlightController(
    private val service: StoryService,
    private val resolver: AuthenticatedAccountResolver
) {
    @GetMapping("/me/stories/archive")
    @Operation(
        summary = "List all successfully published stories owned by the current account",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun archive(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalized(service.archive(resolver.resolve(auth), cursor, limit))

    @PostMapping("/highlights")
    @Operation(
        summary = "Create a named highlight from owned published stories",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun create(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @Valid @RequestBody request: UpsertStoryHighlightRequest
    ) = personalized(service.createHighlight(resolver.resolve(auth), request))

    @PutMapping("/highlights/{highlightId}")
    @Operation(
        summary = "Rename a highlight and replace its ordered story list",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun update(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable highlightId: UUID,
        @Valid @RequestBody request: UpsertStoryHighlightRequest
    ) = personalized(service.updateHighlight(resolver.resolve(auth), highlightId, request))

    @DeleteMapping("/highlights/{highlightId}")
    @Operation(
        summary = "Delete an owned highlight without deleting its stories",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun delete(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable highlightId: UUID
    ): ResponseEntity<Void> {
        service.deleteHighlight(resolver.resolve(auth), highlightId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/profiles/{handle}/highlights")
    @Operation(
        summary = "List visible story highlights for a profile",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun byHandle(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable handle: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalized(service.highlightsByHandle(optionalAccountId(auth), handle, cursor, limit))

    @GetMapping("/highlights/{highlightId}")
    @Operation(
        summary = "Get a visible highlight with its ordered stories",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun get(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable highlightId: UUID
    ) = personalized(service.highlight(optionalAccountId(auth), highlightId))

    private fun optionalAccountId(auth: String?): UUID? =
        auth?.takeIf(String::isNotBlank)?.let(resolver::resolve)

    private fun <T> personalized(body: T): ResponseEntity<T> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
        .body(body)
}
