package com.groovsta.content.dto

data class StoryCreateResponse(
    val story: StoryResponse,
    val upload: StoryUploadResponse?
)
