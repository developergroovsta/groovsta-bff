package com.groovsta.messaging.service

import com.groovsta.messaging.dto.ChatConversationPageResponse
import com.groovsta.messaging.dto.ChatMessagePageResponse
import com.groovsta.messaging.dto.ChatMessageResponse
import com.groovsta.messaging.dto.ChatDeliveryReceiptResponse
import com.groovsta.messaging.dto.ChatPresenceResponse
import com.groovsta.messaging.dto.ChatReadReceiptResponse
import com.groovsta.messaging.dto.EditChatMessageRequest
import com.groovsta.messaging.dto.MarkConversationDeliveredRequest
import com.groovsta.messaging.dto.MarkConversationReadRequest
import com.groovsta.messaging.dto.ReactToChatMessageRequest
import com.groovsta.messaging.dto.SendChatMessageRequest
import java.util.UUID

interface ChatService {
    fun conversations(accountId: UUID, cursor: String?, limit: Int): ChatConversationPageResponse
    fun messages(accountId: UUID, conversationId: UUID, cursor: String?, limit: Int): ChatMessagePageResponse
    fun send(accountId: UUID, request: SendChatMessageRequest): ChatMessageResponse
    fun edit(accountId: UUID, messageId: UUID, request: EditChatMessageRequest): ChatMessageResponse
    fun unsend(accountId: UUID, messageId: UUID): ChatMessageResponse
    fun react(accountId: UUID, messageId: UUID, request: ReactToChatMessageRequest): ChatMessageResponse
    fun removeReaction(accountId: UUID, messageId: UUID): ChatMessageResponse
    fun markDelivered(
        accountId: UUID,
        conversationId: UUID,
        request: MarkConversationDeliveredRequest
    ): ChatDeliveryReceiptResponse
    fun markRead(
        accountId: UUID,
        conversationId: UUID,
        request: MarkConversationReadRequest
    ): ChatReadReceiptResponse
    fun presence(accountId: UUID, targetAccountId: UUID): ChatPresenceResponse
}
