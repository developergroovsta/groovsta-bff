package com.groovsta.notification.service.impl

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.google.firebase.messaging.WebpushConfig
import com.groovsta.identity.model.DevicePlatform
import com.groovsta.notification.model.PushIdentifierType
import com.groovsta.notification.service.PushDeliveryWork
import com.groovsta.notification.service.PushGateway
import com.groovsta.notification.service.PushSendResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

@Component
@ConditionalOnProperty(prefix = "groovsta.push", name = ["enabled"], havingValue = "true")
class FirebasePushGateway(
    private val firebaseMessaging: FirebaseMessaging
) : PushGateway {
    @Suppress("DEPRECATION") // FCM registration tokens remain supported while Firebase transitions to FIDs.
    override fun send(work: PushDeliveryWork): PushSendResult = try {
        val builder = Message.builder()
            .putAllData(work.data)
            .setAndroidConfig(androidConfig(work))
            .setApnsConfig(apnsConfig(work))
            .setWebpushConfig(webpushConfig(work))
        if (work.title != null || work.body != null) {
            builder.setNotification(
                Notification.builder()
                    .setTitle(work.title ?: "Groovsta")
                    .setBody(work.body ?: "")
                    .build()
            )
        }
        when (work.identifierType) {
            PushIdentifierType.FCM_TOKEN -> builder.setToken(work.registrationId)
            PushIdentifierType.FIREBASE_INSTALLATION_ID -> builder.setFid(work.registrationId)
        }
        PushSendResult.Sent(firebaseMessaging.send(builder.build()))
    } catch (exception: FirebaseMessagingException) {
        val code = exception.messagingErrorCode?.name ?: exception.errorCode.name
        when (code) {
            "UNREGISTERED" -> PushSendResult.PermanentFailure(code, invalidateRegistration = true)
            "UNAVAILABLE", "INTERNAL", "QUOTA_EXCEEDED" -> PushSendResult.Retryable(code)
            else -> PushSendResult.PermanentFailure(code, invalidateRegistration = false)
        }
    } catch (exception: Exception) {
        PushSendResult.Retryable("FCM_${exception.javaClass.simpleName.uppercase()}")
    }

    private fun androidConfig(work: PushDeliveryWork): AndroidConfig {
        val builder = AndroidConfig.builder()
            .setPriority(if (work.highPriority) AndroidConfig.Priority.HIGH else AndroidConfig.Priority.NORMAL)
            .setTtl(work.ttlSeconds * 1_000)
        work.collapseKey?.let(builder::setCollapseKey)
        if (work.title != null || work.body != null) {
            builder.setNotification(
                AndroidNotification.builder()
                    .setChannelId(if (work.messageType == CALL_MESSAGE_TYPE) "calls" else "social")
                    .setTag(work.collapseKey ?: work.deliveryId.toString())
                    .setSound("default")
                    .build()
            )
        }
        return builder.build()
    }

    private fun apnsConfig(work: PushDeliveryWork): ApnsConfig {
        val visible = work.title != null || work.body != null
        val builder = ApnsConfig.builder()
            .putHeader("apns-priority", if (visible && work.highPriority) "10" else "5")
            .putHeader("apns-expiration", Instant.now().plusSeconds(work.ttlSeconds).epochSecond.toString())
        work.collapseKey?.let { builder.putHeader("apns-collapse-id", it.take(64)) }
        val aps = Aps.builder()
            .setContentAvailable(!visible)
            .apply {
                work.data["unreadCount"]?.toIntOrNull()?.let(::setBadge)
                if (visible) setSound("default")
                if (work.messageType == CALL_MESSAGE_TYPE) setCategory("GROOVSTA_CALL")
            }
            .build()
        return builder.setAps(aps).build()
    }

    private fun webpushConfig(work: PushDeliveryWork): WebpushConfig = WebpushConfig.builder()
        .putHeader("TTL", work.ttlSeconds.toString())
        .putHeader("Urgency", if (work.highPriority) "high" else "normal")
        .build()

    private companion object {
        const val CALL_MESSAGE_TYPE = "CALL"
    }
}
