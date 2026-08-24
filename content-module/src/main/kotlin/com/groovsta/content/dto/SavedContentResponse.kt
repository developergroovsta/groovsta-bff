package com.groovsta.content.dto

import java.time.Instant
import java.util.UUID

data class SavedContentResponse(
    val contentId: UUID,
    val saved: Boolean,
    val savedAt: Instant?
)
