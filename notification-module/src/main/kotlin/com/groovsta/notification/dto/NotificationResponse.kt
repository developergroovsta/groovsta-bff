package com.groovsta.notification.dto

import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val type: NotificationType,
    val actor: ProfileSummaryResponse?,
    val targetType: NotificationTargetType,
    val targetId: UUID,
    val referenceId: UUID,
    val contextId: UUID?,
    val preview: String?,
    val read: Boolean,
    val actionRequired: Boolean,
    val createdAt: Instant
)
