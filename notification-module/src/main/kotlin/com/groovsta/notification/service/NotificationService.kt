package com.groovsta.notification.service

import com.groovsta.notification.dto.NotificationPageResponse
import com.groovsta.notification.dto.NotificationResponse
import com.groovsta.notification.dto.UnreadNotificationCountResponse
import java.util.UUID

interface NotificationService {
    fun inbox(accountId: UUID, cursor: String?, limit: Int, unreadOnly: Boolean): NotificationPageResponse
    fun unreadCount(accountId: UUID): UnreadNotificationCountResponse
    fun markRead(accountId: UUID, notificationId: UUID): NotificationResponse
    fun markAllRead(accountId: UUID)
}
