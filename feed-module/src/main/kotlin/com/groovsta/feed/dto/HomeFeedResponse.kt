package com.groovsta.feed.dto

import com.groovsta.content.dto.ContentFeedItemResponse

data class HomeFeedResponse(
    val items: List<ContentFeedItemResponse>,
    val nextCursor: String?
)
