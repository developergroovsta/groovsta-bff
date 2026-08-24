package com.groovsta.messaging.dto

enum class ChatEventType {
    MESSAGE_CREATED,
    MESSAGE_UPDATED,
    MESSAGE_UNSENT,
    MESSAGE_REACTION_UPDATED,
    MESSAGE_DELIVERED,
    MESSAGE_READ,
    PRESENCE_CHANGED
}

data class ChatEventResponse(
    val type: ChatEventType,
    val message: ChatMessageResponse? = null,
    val readReceipt: ChatReadReceiptResponse? = null,
    val deliveryReceipt: ChatDeliveryReceiptResponse? = null,
    val presence: ChatPresenceResponse? = null
)
