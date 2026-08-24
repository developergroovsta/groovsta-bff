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
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/content/{contentId}")
@Tag(name = "Content Engagement")
class EngagementController(private val service: EngagementService, private val resolver: AuthenticatedAccountResolver) {
    @GetMapping("/likes")
    @Operation(
        summary = "List users who liked a published post or reel",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun likers(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable contentId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = service.likers(optionalAccountId(auth), contentId, cursor, limit)

    @PostMapping("/likes")
    @Operation(summary = "Like a published post or reel", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun like(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?, @PathVariable contentId: UUID) = service.like(resolver.resolve(auth), contentId)

    @DeleteMapping("/likes")
    @Operation(summary = "Remove a content like", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun unlike(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?, @PathVariable contentId: UUID) = service.unlike(resolver.resolve(auth), contentId)

    @PostMapping("/comments")
    @Operation(summary = "Comment on a published post or reel", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun comment(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?, @PathVariable contentId: UUID, @Valid @RequestBody request: CreateCommentRequest) = service.comment(resolver.resolve(auth), contentId, request)

    @GetMapping("/comments")
    @Operation(
        summary = "List top-level content comments",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun comments(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable contentId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = service.comments(optionalAccountId(auth), contentId, cursor, limit)

    @GetMapping("/comments/{commentId}/replies")
    @Operation(
        summary = "List replies in a comment thread",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun replies(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable contentId: UUID,
        @PathVariable commentId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = service.replies(optionalAccountId(auth), contentId, commentId, cursor, limit)

    @PostMapping("/comments/{commentId}/likes")
    @Operation(summary = "Like a comment", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun likeComment(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable contentId: UUID,
        @PathVariable commentId: UUID
    ) = service.likeComment(resolver.resolve(auth), contentId, commentId)

    @DeleteMapping("/comments/{commentId}/likes")
    @Operation(summary = "Remove a comment like", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun unlikeComment(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable contentId: UUID,
        @PathVariable commentId: UUID
    ) = service.unlikeComment(resolver.resolve(auth), contentId, commentId)

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete own comment", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun deleteComment(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?, @PathVariable contentId: UUID, @PathVariable commentId: UUID) = service.deleteComment(resolver.resolve(auth), contentId, commentId)

    private fun optionalAccountId(auth: String?) = auth?.takeIf(String::isNotBlank)?.let(resolver::resolve)
}
