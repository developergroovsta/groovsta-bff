package com.groovsta.notification.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.groovsta.notification.model.PushDeliveryStatus
import com.groovsta.notification.repository.PushDeliveryRepository
import com.groovsta.notification.repository.PushRegistrationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class PushDeliveryLoader(
    private val deliveryRepository: PushDeliveryRepository,
    private val registrationRepository: PushRegistrationRepository,
    private val objectMapper: ObjectMapper
) {
    @Transactional(readOnly = true)
    fun load(deliveryId: UUID): PushDeliveryWork? {
        val delivery = deliveryRepository.findById(deliveryId).orElse(null) ?: return null
        if (delivery.status != PushDeliveryStatus.PROCESSING) return null
        val registration = registrationRepository.findById(delivery.pushRegistrationId).orElse(null) ?: return null
        if (registration.disabledAt != null) return null
        val data: Map<String, String> = objectMapper.readValue(
            delivery.dataPayload,
            object : TypeReference<Map<String, String>>() {}
        )
        return PushDeliveryWork(
            deliveryId = delivery.id,
            registrationId = registration.registrationId,
            identifierType = registration.identifierType,
            platform = registration.platform,
            messageType = delivery.messageType,
            title = delivery.title,
            body = delivery.body,
            data = data,
            highPriority = delivery.highPriority,
            ttlSeconds = delivery.ttlSeconds,
            collapseKey = delivery.collapseKey
        )
    }
}
