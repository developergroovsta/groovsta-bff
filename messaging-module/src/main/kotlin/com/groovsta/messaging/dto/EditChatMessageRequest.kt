package com.groovsta.messaging.dto

import jakarta.validation.constraints.Size

data class EditChatMessageRequest(
    @Size(max = 2000)
    val text: String?
)
