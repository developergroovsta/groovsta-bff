package com.groovsta.content.dto

import java.util.UUID

data class PostMediaResponse(
    val id: UUID,
    val position: Int,
    val contentType: String,
    val imageUrl: String
)
