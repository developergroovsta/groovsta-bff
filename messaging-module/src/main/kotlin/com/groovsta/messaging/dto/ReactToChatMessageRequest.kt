package com.groovsta.messaging.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ReactToChatMessageRequest(
    @NotBlank
    @Size(max = 32)
    val emoji: String
)
