package com.groovsta.content.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreatePostUploadRequest(
    @Size(max = 2200)
    val caption: String?,
    @Valid
    @Size(min = 1, max = 10, message = "A post must contain between 1 and 10 images")
    val media: List<CreatePostMediaUploadRequest>,
    @Size(max = 20)
    val taggedAccountIds: List<UUID> = emptyList(),
    val locationId: UUID? = null,
    val commentsEnabled: Boolean = true,
    val hideComments: Boolean = false,
    val hideLikeCount: Boolean = false,
    val hideShareCount: Boolean = false
)
