package com.groovsta.notification.service

import com.groovsta.identity.model.DevicePlatform
import com.groovsta.notification.model.PushIdentifierType
import java.util.UUID

data class PushDeliveryWork(
    val deliveryId: UUID,
    val registrationId: String,
    val identifierType: PushIdentifierType,
    val platform: DevicePlatform,
    val messageType: String,
    val title: String?,
    val body: String?,
    val data: Map<String, String>,
    val highPriority: Boolean,
    val ttlSeconds: Long,
    val collapseKey: String?
)
