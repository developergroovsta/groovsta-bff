package com.groovsta.content.dto

import java.util.UUID

data class PostUploadResponse(
    val postId: UUID,
    val uploads: List<PostMediaUploadResponse>
)
