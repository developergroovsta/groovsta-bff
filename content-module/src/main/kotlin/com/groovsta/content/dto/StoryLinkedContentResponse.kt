package com.groovsta.content.dto

import com.groovsta.content.model.ContentKind
import java.util.UUID

data class StoryLinkedContentResponse(
    val contentId: UUID,
    val type: ContentKind
)
