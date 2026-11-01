package com.groovsta.messaging.controller

import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import com.groovsta.messaging.dto.CreateChatAttachmentUploadRequest
import com.groovsta.messaging.service.ChatAttachmentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/chat/attachments/uploads")
@Tag(name = "Chat Attachments")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
class ChatAttachmentController(
    private val service: ChatAttachmentService,
    private val resolver: AuthenticatedAccountResolver
) {
    @PostMapping
    @Operation(summary = "Initiate one or more private image/video uploads for a media message")
    fun createUpload(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: CreateChatAttachmentUploadRequest
    ) = personalized(
        service.createUpload(resolver.resolve(authorization), request),
        HttpStatus.CREATED
    )

    @PostMapping("/{uploadBatchId}/complete")
    @Operation(summary = "Verify an attachment batch in S3 and atomically create the media message")
    fun completeUpload(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable uploadBatchId: UUID
    ) = personalized(service.completeUpload(resolver.resolve(authorization), uploadBatchId))

    private fun <T> personalized(body: T, status: HttpStatus = HttpStatus.OK): ResponseEntity<T> =
        ResponseEntity.status(status)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
            .body(body)
}
