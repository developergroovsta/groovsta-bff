package com.groovsta.messaging.controller

import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import com.groovsta.messaging.dto.AddCallParticipantsRequest
import com.groovsta.messaging.dto.CallHeartbeatRequest
import com.groovsta.messaging.dto.CallMediaStateRequest
import com.groovsta.messaging.dto.CreateCallRequest
import com.groovsta.messaging.service.CallService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/calls")
@Tag(name = "Audio and video calls")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
class CallController(
    private val service: CallService,
    private val resolver: AuthenticatedAccountResolver
) {
    @PostMapping
    @Operation(summary = "Start a direct or group audio/video call")
    fun create(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: CreateCallRequest
    ) = personalized(service.create(resolver.resolve(authorization), request), HttpStatus.CREATED)

    @GetMapping
    @Operation(summary = "List the current account's call history")
    fun history(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = personalized(service.history(resolver.resolve(authorization), cursor, limit))

    @GetMapping("/active")
    @Operation(summary = "Get ringing and active calls for reconnect/catch-up")
    fun active(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?
    ) = personalized(service.active(resolver.resolve(authorization)))

    @GetMapping("/ice-configuration")
    @Operation(summary = "Get STUN/TURN configuration for WebRTC")
    fun iceConfiguration(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?
    ) = personalized(service.iceConfiguration(resolver.resolve(authorization)))

    @GetMapping("/{callId}")
    @Operation(summary = "Get one call and all participant states")
    fun get(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable callId: UUID
    ) = personalized(service.get(resolver.resolve(authorization), callId))

    @PostMapping("/{callId}/accept")
    @Operation(summary = "Accept a pending call invitation")
    fun accept(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable callId: UUID
    ) = personalized(service.accept(resolver.resolve(authorization), callId))

    @PostMapping("/{callId}/decline")
    @Operation(summary = "Decline a pending call invitation")
    fun decline(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable callId: UUID
    ) = personalized(service.decline(resolver.resolve(authorization), callId))

    @PostMapping("/{callId}/cancel")
    @Operation(summary = "Cancel a ringing call as its initiator")
    fun cancel(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable callId: UUID
    ) = personalized(service.cancel(resolver.resolve(authorization), callId))

    @PostMapping("/{callId}/leave")
    @Operation(summary = "Leave an active call without necessarily ending a group call")
    fun leave(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable callId: UUID
    ) = personalized(service.leave(resolver.resolve(authorization), callId))

    @PostMapping("/{callId}/end")
    @Operation(summary = "End a call for everyone as its current host")
    fun end(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable callId: UUID
    ) = personalized(service.end(resolver.resolve(authorization), callId))

    @PostMapping("/{callId}/participants")
    @Operation(summary = "Invite more participants to an ongoing call as host")
    fun invite(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable callId: UUID,
        @Valid @RequestBody request: AddCallParticipantsRequest
    ) = personalized(service.invite(resolver.resolve(authorization), callId, request))

    @PatchMapping("/{callId}/media-state")
    @Operation(summary = "Update microphone/camera state; WebSocket is preferred during a call")
    fun updateMedia(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable callId: UUID,
        @RequestBody request: CallMediaStateRequest
    ) = personalized(service.updateMedia(resolver.resolve(authorization), callId, request))

    @PostMapping("/heartbeat")
    @Operation(summary = "Keep a joined call participant active; WebSocket is preferred")
    fun heartbeat(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String?,
        @RequestBody request: CallHeartbeatRequest
    ): ResponseEntity<Void> {
        service.heartbeat(resolver.resolve(authorization), request)
        return ResponseEntity.noContent()
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .build()
    }

    private fun <T> personalized(body: T, status: HttpStatus = HttpStatus.OK): ResponseEntity<T> =
        ResponseEntity.status(status)
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
            .body(body)
}
