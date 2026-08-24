package com.groovsta.content.dto

import java.util.UUID

data class TranscodeJobResponse(
    val jobId: UUID,
    val targetType: TranscodeTargetType,
    val targetId: UUID,
    val reelId: UUID?,
    val storyId: UUID?,
    val chatAttachmentId: UUID?,
    val sourceObjectKey: String,
    val outputPrefix: String
)
