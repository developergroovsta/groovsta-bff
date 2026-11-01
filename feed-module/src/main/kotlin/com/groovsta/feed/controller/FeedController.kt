package com.groovsta.feed.controller

import com.groovsta.feed.service.FeedService
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Feeds")
class FeedController(private val service: FeedService, private val resolver: AuthenticatedAccountResolver) {
    @GetMapping("/feeds/reels")
    @Operation(
        summary = "Get the latest reels from public profiles",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun reels(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @RequestParam(required = false) before: Instant?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalizedResponse(
        service.reels(optionalAccountId(auth), before ?: Instant.now().plusMillis(1), limit)
    )

    @GetMapping("/feeds/home")
    @Operation(
        summary = "Get followed content first, followed by public content",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun home(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalizedResponse(service.home(optionalAccountId(auth), cursor, limit))

    @GetMapping("/feeds/stories")
    @Operation(
        summary = "Get active stories visible to the viewer",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun stories(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalizedResponse(service.stories(optionalAccountId(auth), cursor, limit))

    @GetMapping("/profiles/{handle}/posts")
    @Operation(
        summary = "Get the published posts displayed on a profile",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun profilePosts(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable handle: String,
        @RequestParam(required = false) before: Instant?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = service.profilePosts(optionalAccountId(auth), handle, before ?: Instant.now().plusMillis(1), limit)

    @GetMapping("/profiles/{handle}/reels")
    @Operation(
        summary = "Get the published reels displayed on a profile",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun profileReels(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable handle: String,
        @RequestParam(required = false) before: Instant?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = service.profileReels(optionalAccountId(auth), handle, before ?: Instant.now().plusMillis(1), limit)

    @GetMapping("/profiles/{handle}/stories")
    @Operation(
        summary = "Get active stories for a profile that are visible to the viewer",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun profileStories(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable handle: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalizedResponse(service.profileStories(optionalAccountId(auth), handle, cursor, limit))

    private fun optionalAccountId(auth: String?) = auth?.takeIf(String::isNotBlank)?.let(resolver::resolve)

    private fun <T> personalizedResponse(body: T): ResponseEntity<T> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
        .body(body)
}
