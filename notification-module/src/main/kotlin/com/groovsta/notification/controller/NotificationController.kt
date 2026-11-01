package com.groovsta.notification.controller

import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import com.groovsta.notification.service.NotificationService
import com.groovsta.notification.service.PushRegistrationService
import com.groovsta.notification.dto.RegisterPushTokenRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import jakarta.validation.Valid

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
class NotificationController(
    private val service: NotificationService,
    private val pushRegistrationService: PushRegistrationService,
    private val resolver: AuthenticatedAccountResolver
) {

    @GetMapping
    @Operation(summary = "List notifications")
    fun inbox(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean
    ) = service.inbox(resolver.resolve(authorization), cursor, limit, unreadOnly)

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notification count")
    fun unreadCount(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?
    ) = service.unreadCount(resolver.resolve(authorization))

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark one notification as read")
    fun markRead(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable notificationId: UUID
    ) = service.markRead(resolver.resolve(authorization), notificationId)

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    fun markAllRead(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?
    ): ResponseEntity<Void> {
        service.markAllRead(resolver.resolve(authorization))
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/push-token")
    @Operation(summary = "Register or rotate this authenticated installation's FCM token or Firebase Installation ID")
    fun registerPushToken(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @RequestHeader("X-Device-Id") deviceIdentifier: String,
        @Valid @RequestBody request: RegisterPushTokenRequest
    ) = pushRegistrationService.register(
        resolver.resolve(authorization),
        deviceIdentifier,
        request
    )

    @DeleteMapping("/push-token")
    @Operation(summary = "Disable push delivery for this authenticated installation")
    fun unregisterPushToken(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @RequestHeader("X-Device-Id") deviceIdentifier: String
    ): ResponseEntity<Void> {
        pushRegistrationService.unregister(resolver.resolve(authorization), deviceIdentifier)
        return ResponseEntity.noContent().build()
    }
}
