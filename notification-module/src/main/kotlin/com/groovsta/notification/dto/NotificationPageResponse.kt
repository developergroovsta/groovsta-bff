package com.groovsta.notification.dto

data class NotificationPageResponse(
    val items: List<NotificationResponse>,
    val nextCursor: String?
)
