package com.groovsta.content.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.Valid
import java.util.UUID

data class CreateCommentRequest(
    @NotBlank @Size(max = 500) val text: String,
    val replyToCommentId: UUID? = null,
    @Valid @Size(max = 20) val mentions: List<CreateCommentMentionRequest> = emptyList()
)
