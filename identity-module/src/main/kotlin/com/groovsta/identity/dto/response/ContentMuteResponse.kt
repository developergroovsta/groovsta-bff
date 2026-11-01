package com.groovsta.identity.dto.response

import com.groovsta.identity.model.ContentMuteType
import java.util.UUID

data class ContentMuteResponse(
    val accountId: UUID,
    val handle: String,
    val contentType: ContentMuteType,
    val muted: Boolean
)
