package com.groovsta.content.dto

data class StoryHighlightPageResponse(
    val items: List<StoryHighlightSummaryResponse>,
    val nextCursor: String?
)
