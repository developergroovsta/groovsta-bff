package com.groovsta.notification.service.impl

import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.service.ProfileService
import com.groovsta.notification.dto.NotificationPageResponse
import com.groovsta.notification.dto.NotificationResponse
import com.groovsta.notification.dto.NotificationRealtimeEventResponse
import com.groovsta.notification.dto.NotificationRealtimeEventType
import com.groovsta.notification.dto.UnreadNotificationCountResponse
import com.groovsta.notification.config.PushProperties
import com.groovsta.notification.model.Notification
import com.groovsta.notification.repository.NotificationRepository
import com.groovsta.notification.service.NotificationService
import com.groovsta.notification.service.NotificationCommittedEvent
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.PushMessageCommand
import com.groovsta.shared.notification.PushMessagePublisher
import com.groovsta.shared.notification.NotificationType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class NotificationServiceImpl(
    private val repository: NotificationRepository,
    private val profileService: ProfileService,
    private val pushMessagePublisher: PushMessagePublisher,
    private val pushProperties: PushProperties,
    private val eventPublisher: ApplicationEventPublisher
) : NotificationService, NotificationPublisher {

    @Transactional
    override fun publish(command: NotificationCommand) {
        if (command.recipientAccountId() == command.actorAccountId()) return
        val notificationId = UUID.randomUUID()
        val inserted = repository.insertIfAbsent(
            id = notificationId,
            recipientAccountId = command.recipientAccountId(),
            actorAccountId = command.actorAccountId(),
            notificationType = command.type().name,
            targetType = command.targetType().name,
            targetId = command.targetId(),
            referenceId = command.referenceId(),
            contextId = command.contextId(),
            preview = command.preview()?.trim()?.take(280)?.takeIf(String::isNotEmpty),
            createdAt = Instant.now()
        )
        if (inserted == 0) return
        val notification = repository.findById(notificationId).orElseThrow {
            IllegalStateException("Created notification could not be loaded")
        }
        val actor = notification.actorAccountId?.let { profileService.summaries(setOf(it))[it] }
        val response = response(notification, actor)
        pushMessagePublisher.publish(
            pushCommand(
                notification,
                actor?.displayName ?: actor?.handle ?: "Someone",
                repository.countByRecipientAccountIdAndReadAtIsNull(notification.recipientAccountId)
            )
        )
        publishRealtime(notification.recipientAccountId, NotificationRealtimeEventType.CREATED, response)
    }

    @Transactional
    override fun resolve(type: NotificationType, referenceId: UUID) {
        val notification = repository.findByTypeAndReferenceId(type, referenceId) ?: return
        if (notification.resolvedAt != null) return
        notification.resolve(Instant.now())
        val actor = notification.actorAccountId?.let { profileService.summaries(setOf(it))[it] }
        publishRealtime(
            notification.recipientAccountId,
            NotificationRealtimeEventType.UPDATED,
            response(notification, actor)
        )
    }

    @Transactional(readOnly = true)
    override fun inbox(
        accountId: UUID,
        cursor: String?,
        limit: Int,
        unreadOnly: Boolean
    ): NotificationPageResponse {
        val pageSize = limit.coerceIn(1, 50)
        val pageable = PageRequest.of(0, pageSize + 1)
        val fetched = cursor?.let(NotificationCursor::decode)?.let {
            repository.findInboxAfter(accountId, unreadOnly, it.createdAt, it.id, pageable)
        } ?: repository.findInbox(accountId, unreadOnly, pageable)
        val page = fetched.take(pageSize)
        val actors = profileService.summaries(page.mapNotNullTo(mutableSetOf(), Notification::actorAccountId))
        return NotificationPageResponse(
            items = page.map { response(it, actors[it.actorAccountId]) },
            nextCursor = page.lastOrNull()
                ?.takeIf { fetched.size > pageSize }
                ?.let(NotificationCursor::from)
                ?.encode()
        )
    }

    @Transactional(readOnly = true)
    override fun unreadCount(accountId: UUID) =
        UnreadNotificationCountResponse(repository.countByRecipientAccountIdAndReadAtIsNull(accountId))

    @Transactional
    override fun markRead(accountId: UUID, notificationId: UUID): NotificationResponse {
        val notification = repository.findById(notificationId).orElseThrow {
            IdentityNotFoundException("Notification was not found")
        }
        if (notification.recipientAccountId != accountId) {
            throw IdentityNotFoundException("Notification was not found")
        }
        notification.markRead(Instant.now())
        val actor = notification.actorAccountId?.let { profileService.summaries(setOf(it))[it] }
        val response = response(notification, actor)
        publishRealtime(accountId, NotificationRealtimeEventType.READ, response)
        return response
    }

    @Transactional
    override fun markAllRead(accountId: UUID) {
        if (repository.markAllRead(accountId, Instant.now()) > 0) {
            publishRealtime(accountId, NotificationRealtimeEventType.ALL_READ, null)
        }
    }

    private fun response(
        notification: Notification,
        actor: com.groovsta.identity.dto.response.ProfileSummaryResponse?
    ) = NotificationResponse(
        id = notification.id,
        type = notification.type,
        actor = actor,
        targetType = notification.targetType,
        targetId = notification.targetId,
        referenceId = notification.referenceId,
        contextId = notification.contextId,
        preview = notification.preview,
        read = notification.readAt != null,
        actionRequired = notification.type == NotificationType.FOLLOW_REQUEST && notification.resolvedAt == null,
        createdAt = notification.createdAt
    )

    private fun pushCommand(notification: Notification, actorName: String, unreadCount: Long): PushMessageCommand {
        val (title, defaultBody) = when (notification.type) {
            NotificationType.FOLLOW_REQUEST -> "New follow request" to "$actorName requested to follow you"
            NotificationType.NEW_FOLLOWER -> "New follower" to "$actorName started following you"
            NotificationType.FOLLOW_REQUEST_ACCEPTED -> "Follow request accepted" to "$actorName accepted your request"
            NotificationType.CONTENT_LIKED -> "New like" to "$actorName liked your post or reel"
            NotificationType.CONTENT_TAGGED -> "You were tagged" to "$actorName tagged you in a post or reel"
            NotificationType.STORY_LIKED -> "New story like" to "$actorName liked your story"
            NotificationType.CONTENT_COMMENTED -> "New comment" to "$actorName commented on your post or reel"
            NotificationType.STORY_COMMENTED -> "New story comment" to "$actorName commented on your story"
            NotificationType.COMMENT_LIKED -> "Comment liked" to "$actorName liked your comment"
            NotificationType.COMMENT_MENTIONED -> "You were mentioned" to "$actorName mentioned you in a comment"
            NotificationType.DIRECT_MESSAGE -> "New message from $actorName" to "Open Groovsta to view the message"
            NotificationType.MISSED_CALL -> "Missed call" to "You missed a call from $actorName"
            NotificationType.REWARD_MILESTONE_ACHIEVED -> "Reward earned" to "You reached a reward milestone"
        }
        val sensitive = notification.type in SENSITIVE_PREVIEW_TYPES
        val safeMediaPreview = notification.type == NotificationType.DIRECT_MESSAGE &&
            notification.preview?.let(SAFE_MEDIA_PREVIEW::matches) == true
        val body = if ((!sensitive || safeMediaPreview || pushProperties.includeSensitivePreviews) && !notification.preview.isNullOrBlank()) {
            notification.preview!!.take(280)
        } else {
            defaultBody
        }
        val data = linkedMapOf(
            "eventType" to "IN_APP_NOTIFICATION",
            "notificationId" to notification.id.toString(),
            "notificationType" to notification.type.name,
            "targetType" to notification.targetType.name,
            "targetId" to notification.targetId.toString(),
            "referenceId" to notification.referenceId.toString(),
            "unreadCount" to unreadCount.toString()
        )
        notification.contextId?.let { data["contextId"] = it.toString() }
        return PushMessageCommand(
            notification.recipientAccountId,
            notification.id,
            "notification:${notification.id}",
            "NOTIFICATION",
            title,
            body,
            data,
            notification.type in HIGH_PRIORITY_TYPES,
            pushProperties.notificationTtl.seconds,
            if (notification.type == NotificationType.MISSED_CALL) "call-${notification.targetId}" else null
        )
    }

    private fun publishRealtime(
        recipientAccountId: UUID,
        type: NotificationRealtimeEventType,
        notification: NotificationResponse?
    ) {
        eventPublisher.publishEvent(
            NotificationCommittedEvent(
                recipientAccountId,
                NotificationRealtimeEventResponse(type = type, notification = notification)
            )
        )
    }

    private data class NotificationCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$createdAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(notification: Notification) = NotificationCursor(notification.createdAt, notification.id)

            fun decode(value: String): NotificationCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid notification cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid notification cursor" }
                return runCatching { NotificationCursor(Instant.parse(parts[0]), UUID.fromString(parts[1])) }
                    .getOrElse { throw IllegalArgumentException("Invalid notification cursor") }
            }
        }
    }

    private companion object {
        val SENSITIVE_PREVIEW_TYPES = setOf(
            NotificationType.DIRECT_MESSAGE,
            NotificationType.CONTENT_COMMENTED,
            NotificationType.STORY_COMMENTED,
            NotificationType.COMMENT_MENTIONED
        )
        val HIGH_PRIORITY_TYPES = setOf(
            NotificationType.FOLLOW_REQUEST,
            NotificationType.DIRECT_MESSAGE,
            NotificationType.MISSED_CALL,
            NotificationType.REWARD_MILESTONE_ACHIEVED
        )
        val SAFE_MEDIA_PREVIEW = Regex("Sent (a photo|a video|[1-9][0-9]* photos|[1-9][0-9]* videos|[1-9][0-9]* photos and videos)")
    }
}
