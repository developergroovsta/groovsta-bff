package com.groovsta.messaging.controller

import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.messaging.dto.CallHeartbeatRequest
import com.groovsta.messaging.dto.CallMediaStateRequest
import com.groovsta.messaging.dto.CallSignalRequest
import com.groovsta.messaging.dto.ChatSocketErrorResponse
import com.groovsta.messaging.dto.SocketCallMediaStateRequest
import com.groovsta.messaging.service.CallService
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory
import java.security.Principal
import java.util.UUID

@Controller
class CallSocketController(
    private val service: CallService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @MessageMapping("/call.signal")
    fun signal(@Valid request: CallSignalRequest, principal: Principal) {
        log.debug(
            "ws.call.signal inbound senderAccountId={} recipientAccountId={} callId={} type={}",
            principal.name,
            request.recipientAccountId,
            request.callId,
            request.type
        )
        service.relaySignal(accountId(principal), request)
    }

    @MessageMapping("/call.media-state")
    fun mediaState(request: SocketCallMediaStateRequest, principal: Principal) {
        log.debug(
            "ws.call.media-state inbound accountId={} callId={} microphoneMuted={} cameraEnabled={}",
            principal.name,
            request.callId,
            request.microphoneMuted,
            request.cameraEnabled
        )
        service.updateMedia(
            accountId(principal),
            request.callId,
            CallMediaStateRequest(request.microphoneMuted, request.cameraEnabled)
        )
    }

    @MessageMapping("/call.heartbeat")
    fun heartbeat(request: CallHeartbeatRequest, principal: Principal) {
        log.debug("ws.call.heartbeat inbound accountId={} callId={}", principal.name, request.callId)
        service.heartbeat(accountId(principal), request)
    }

    @MessageExceptionHandler(
        IllegalArgumentException::class,
        IdentityConflictException::class,
        ResponseStatusException::class
    )
    @SendToUser(destinations = ["/queue/call-errors"], broadcast = false)
    fun callError(exception: Exception): ChatSocketErrorResponse = ChatSocketErrorResponse(
        code = when (exception) {
            is ResponseStatusException -> exception.statusCode.toString().replace(' ', '_')
            is IdentityConflictException -> "CONFLICT"
            else -> "INVALID_REQUEST"
        },
        message = exception.message ?: "Call request failed"
    )

    private fun accountId(principal: Principal): UUID = UUID.fromString(principal.name)
}
