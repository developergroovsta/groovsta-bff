package com.groovsta.content.controller

import com.groovsta.content.dto.CreateCommentRequest
import com.groovsta.content.service.EngagementService
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stories/{storyId}")
@Tag(name = "Story Engagement")
class StoryEngagementController(
    private val service: EngagementService,
    private val resolver: AuthenticatedAccountResolver
) {
    @GetMapping("/likes")
    @Operation(
        summary = "List users who liked a visible story",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun likers(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable storyId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalized(service.storyLikers(optionalAccountId(auth), storyId, cursor, limit))

    @PostMapping("/likes")
    @Operation(summary = "Like a visible story", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun like(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID
    ) = service.likeStory(resolver.resolve(auth), storyId)

    @DeleteMapping("/likes")
    @Operation(summary = "Remove a story like", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun unlike(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID
    ) = service.unlikeStory(resolver.resolve(auth), storyId)

    @PostMapping("/comments")
    @Operation(summary = "Comment on or reply within a visible story", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun comment(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID,
        @Valid @RequestBody request: CreateCommentRequest
    ) = service.commentOnStory(resolver.resolve(auth), storyId, request)

    @GetMapping("/comments")
    @Operation(
        summary = "List top-level comments on a visible story",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun comments(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable storyId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalized(service.storyComments(optionalAccountId(auth), storyId, cursor, limit))

    @GetMapping("/comments/{commentId}/replies")
    @Operation(
        summary = "List replies in a visible story comment thread",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun replies(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable storyId: UUID,
        @PathVariable commentId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalized(service.storyReplies(optionalAccountId(auth), storyId, commentId, cursor, limit))

    @PostMapping("/comments/{commentId}/likes")
    @Operation(summary = "Like a story comment", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun likeComment(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID,
        @PathVariable commentId: UUID
    ) = service.likeStoryComment(resolver.resolve(auth), storyId, commentId)

    @DeleteMapping("/comments/{commentId}/likes")
    @Operation(summary = "Remove a story comment like", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun unlikeComment(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID,
        @PathVariable commentId: UUID
    ) = service.unlikeStoryComment(resolver.resolve(auth), storyId, commentId)

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete an own story comment", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun deleteComment(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable storyId: UUID,
        @PathVariable commentId: UUID
    ): ResponseEntity<Void> {
        service.deleteStoryComment(resolver.resolve(auth), storyId, commentId)
        return ResponseEntity.noContent().build()
    }

    private fun optionalAccountId(auth: String?): UUID? =
        auth?.takeIf(String::isNotBlank)?.let(resolver::resolve)

    private fun <T> personalized(body: T): ResponseEntity<T> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
        .body(body)
}
