package com.groovsta.feed.dto

import com.groovsta.content.dto.ReelResponse

data class FeedResponse(val items: List<ReelResponse>, val nextCursor: String?)
