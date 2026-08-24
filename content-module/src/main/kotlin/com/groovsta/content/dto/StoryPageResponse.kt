package com.groovsta.content.dto

data class StoryPageResponse(
    val items: List<StoryResponse>,
    val nextCursor: String?
)
