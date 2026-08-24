package com.groovsta.content.dto

data class CommentPageResponse(
    val items: List<CommentResponse>,
    val nextCursor: String?
)
