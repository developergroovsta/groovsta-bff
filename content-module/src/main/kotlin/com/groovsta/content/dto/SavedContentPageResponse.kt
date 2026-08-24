package com.groovsta.content.dto

data class SavedContentPageResponse(
    val items: List<ContentFeedItemResponse>,
    val nextCursor: String?
)
