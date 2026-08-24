package com.groovsta.messaging.dto

data class ChatReactionSummaryResponse(
    val emoji: String,
    val count: Int,
    val reactedByViewer: Boolean
)
