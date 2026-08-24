package com.groovsta.content.dto

import com.groovsta.content.model.ContentKind
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateContentUploadRequest(
    val type: ContentKind,
    @Size(max = 2200)
    val caption: String?,
    @Valid
    @Size(min = 1, max = 10)
    val media: List<CreateContentMediaUploadRequest>,
    @Size(max = 20)
    val taggedAccountIds: List<UUID> = emptyList()
)
