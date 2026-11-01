package com.groovsta.messaging.controller

import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.messaging.dto.ChatSocketErrorResponse
import com.groovsta.messaging.dto.MarkConversationReadRequest
import com.groovsta.messaging.dto.MarkConversationDeliveredRequest
import com.groovsta.messaging.dto.EditChatMessageRequest
import com.groovsta.messaging.dto.ReactToChatMessageRequest
import com.groovsta.messaging.dto.SendChatMessageRequest
import com.groovsta.messaging.service.ChatService
import jakarta.validation.Valid
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.util.UUID

@Controller
class ChatSocketController(
    private val service: ChatService
) {
    @MessageMapping("/chat.send")
    fun send(@Valid request: SendChatMessageRequest, principal: Principal) {
        service.send(accountId(principal), request)
    }

    @MessageMapping("/chat.edit")
    fun edit(request: SocketEditRequest, principal: Principal) {
        service.edit(accountId(principal), request.messageId, EditChatMessageRequest(request.text))
    }

    @MessageMapping("/chat.unsend")
    fun unsend(request: SocketMessageRequest, principal: Principal) {
        service.unsend(accountId(principal), request.messageId)
    }

    @MessageMapping("/chat.react")
    fun react(request: SocketReactionRequest, principal: Principal) {
        service.react(accountId(principal), request.messageId, ReactToChatMessageRequest(request.emoji))
    }

    @MessageMapping("/chat.reaction.remove")
    fun removeReaction(request: SocketMessageRequest, principal: Principal) {
        service.removeReaction(accountId(principal), request.messageId)
    }

    @MessageMapping("/chat.delivered")
    fun delivered(request: SocketConversationMessageRequest, principal: Principal) {
        service.markDelivered(
            accountId(principal),
            request.conversationId,
            MarkConversationDeliveredRequest(request.messageId)
        )
    }

    @MessageMapping("/chat.read")
    fun markRead(request: SocketReadRequest, principal: Principal) {
        service.markRead(
            accountId(principal),
            request.conversationId,
            MarkConversationReadRequest(request.messageId)
        )
    }

    @MessageExceptionHandler(
        IllegalArgumentException::class,
        IdentityConflictException::class,
        ResponseStatusException::class
    )
    @SendToUser(destinations = ["/queue/chat-errors"], broadcast = false)
    fun chatError(exception: Exception): ChatSocketErrorResponse = ChatSocketErrorResponse(
        code = when (exception) {
            is ResponseStatusException -> exception.statusCode.toString().replace(' ', '_')
            is IdentityConflictException -> "CONFLICT"
            else -> "INVALID_REQUEST"
        },
        message = exception.message ?: "Chat request failed"
    )

    private fun accountId(principal: Principal): UUID = UUID.fromString(principal.name)
}

data class SocketConversationMessageRequest(
    val conversationId: UUID,
    val messageId: UUID
)

typealias SocketReadRequest = SocketConversationMessageRequest

data class SocketMessageRequest(val messageId: UUID)
data class SocketEditRequest(val messageId: UUID, val text: String?)
data class SocketReactionRequest(val messageId: UUID, val emoji: String)
