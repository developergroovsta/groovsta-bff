package com.groovsta.content.dto

import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.util.UUID

data class CreateCommentMentionRequest(
    val accountId: UUID,
    @PositiveOrZero val start: Int,
    @Positive val endExclusive: Int
)
