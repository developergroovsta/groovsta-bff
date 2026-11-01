package com.groovsta.feed.dto

import com.groovsta.content.dto.PostResponse

data class PostFeedResponse(
    val items: List<PostResponse>,
    val nextCursor: String?
)
