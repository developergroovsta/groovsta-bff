package com.groovsta.content.dto

import java.util.UUID

data class ShareResponse(
    val targetId: UUID,
    val targetType: ShareTargetType,
    val shared: Boolean,
    val shareCount: Long,
    val shareCountDisplay: String = shareCount.toDisplayCount()
)

enum class ShareTargetType {
    CONTENT,
    STORY
}
