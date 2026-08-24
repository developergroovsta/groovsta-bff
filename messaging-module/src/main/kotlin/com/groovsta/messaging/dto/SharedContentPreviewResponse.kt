package com.groovsta.messaging.dto

import com.groovsta.content.model.ContentKind
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.util.UUID

data class SharedContentPreviewResponse(
    val contentId: UUID,
    val available: Boolean,
    val type: ContentKind?,
    val author: ProfileSummaryResponse?,
    val caption: String?,
    val previewUrl: String?,
    val liked: Boolean,
    val mediaCount: Int?,
    val durationSeconds: Int?
)
