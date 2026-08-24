package com.groovsta.messaging.controller

import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import com.groovsta.messaging.dto.MarkConversationReadRequest
import com.groovsta.messaging.dto.MarkConversationDeliveredRequest
import com.groovsta.messaging.dto.EditChatMessageRequest
import com.groovsta.messaging.dto.ReactToChatMessageRequest
import com.groovsta.messaging.dto.SendChatMessageRequest
import com.groovsta.messaging.service.ChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
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
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat")
class ChatController(
    private val service: ChatService,
    private val resolver: AuthenticatedAccountResolver
) {
    @GetMapping("/conversations")
    @Operation(
        summary = "List users previously chatted with and each last message",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun conversations(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalized(
        service.conversations(resolver.resolve(authorization), cursor, limit)
    )

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(
        summary = "Get a direct conversation's message history",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun messages(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable conversationId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "30") limit: Int
    ) = personalized(
        service.messages(resolver.resolve(authorization), conversationId, cursor, limit)
    )

    @PostMapping("/messages")
    @Operation(
        summary = "Send a text or native post/reel share message; media uses the attachment upload flow",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun send(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: SendChatMessageRequest
    ) = personalized(
        service.send(resolver.resolve(authorization), request),
        HttpStatus.CREATED
    )

    @PatchMapping("/messages/{messageId}")
    @Operation(
        summary = "Edit a sent message within 10 seconds",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun edit(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable messageId: UUID,
        @Valid @RequestBody request: EditChatMessageRequest
    ) = personalized(service.edit(resolver.resolve(authorization), messageId, request))

    @DeleteMapping("/messages/{messageId}")
    @Operation(
        summary = "Unsend a message while retaining its database record",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun unsend(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable messageId: UUID
    ) = personalized(service.unsend(resolver.resolve(authorization), messageId))

    @PutMapping("/messages/{messageId}/reaction")
    @Operation(
        summary = "Add or replace the current account's emoji reaction",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun react(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable messageId: UUID,
        @Valid @RequestBody request: ReactToChatMessageRequest
    ) = personalized(service.react(resolver.resolve(authorization), messageId, request))

    @DeleteMapping("/messages/{messageId}/reaction")
    @Operation(
        summary = "Remove the current account's reaction",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun removeReaction(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable messageId: UUID
    ) = personalized(service.removeReaction(resolver.resolve(authorization), messageId))

    @PostMapping("/conversations/{conversationId}/delivered")
    @Operation(
        summary = "Acknowledge received messages as delivered",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun markDelivered(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable conversationId: UUID,
        @RequestBody request: MarkConversationDeliveredRequest
    ) = personalized(service.markDelivered(resolver.resolve(authorization), conversationId, request))

    @PostMapping("/conversations/{conversationId}/read")
    @Operation(
        summary = "Mark a conversation read through a message",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun markRead(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable conversationId: UUID,
        @RequestBody request: MarkConversationReadRequest
    ) = personalized(
        service.markRead(resolver.resolve(authorization), conversationId, request)
    )

    @GetMapping("/presence/{accountId}")
    @Operation(
        summary = "Get an account's current in-app chat presence",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun presence(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable accountId: UUID
    ) = personalized(service.presence(resolver.resolve(authorization), accountId))

    private fun <T> personalized(body: T, status: HttpStatus = HttpStatus.OK): ResponseEntity<T> =
        ResponseEntity.status(status)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
            .body(body)
}
