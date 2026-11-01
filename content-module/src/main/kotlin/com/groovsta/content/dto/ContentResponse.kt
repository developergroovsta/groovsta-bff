package com.groovsta.content.dto

import com.groovsta.content.model.ContentKind
import java.util.UUID

data class ContentResponse(
    val id: UUID,
    val type: ContentKind,
    val post: PostResponse?,
    val reel: ReelResponse?
)
