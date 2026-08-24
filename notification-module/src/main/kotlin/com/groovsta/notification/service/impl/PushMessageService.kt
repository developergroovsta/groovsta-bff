package com.groovsta.notification.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.groovsta.notification.repository.PushDeliveryRepository
import com.groovsta.notification.repository.PushRegistrationRepository
import com.groovsta.shared.notification.PushMessageCommand
import com.groovsta.shared.notification.PushMessagePublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PushMessageService(
    private val registrationRepository: PushRegistrationRepository,
    private val deliveryRepository: PushDeliveryRepository,
    private val objectMapper: ObjectMapper
) : PushMessagePublisher {
    @Transactional
    override fun publish(command: PushMessageCommand) {
        require(command.deduplicationKey().isNotBlank() && command.deduplicationKey().length <= 255) {
            "Push deduplication key must be 1 to 255 characters"
        }
        require(command.messageType().isNotBlank() && command.messageType().length <= 50) {
            "Push message type must be 1 to 50 characters"
        }
        val ttlSeconds = command.ttlSeconds().coerceIn(0, MAX_TTL_SECONDS)
        val now = Instant.now()
        registrationRepository.findByAccountIdAndDisabledAtIsNull(command.recipientAccountId()).forEach { registration ->
            val deliveryId = UUID.randomUUID()
            val data = LinkedHashMap(command.data()).apply {
                put("pushDeliveryId", deliveryId.toString())
                put("messageType", command.messageType())
            }
            require(data.all { (key, value) -> key.isNotBlank() && key.length <= 128 && value.length <= 2048 }) {
                "Push data contains an invalid key or value"
            }
            val payload = objectMapper.writeValueAsString(data)
            require(payload.toByteArray(Charsets.UTF_8).size <= MAX_DATA_BYTES) {
                "Push data payload is too large"
            }
            deliveryRepository.insertIfAbsent(
                id = deliveryId,
                pushRegistrationId = registration.id,
                recipientAccountId = command.recipientAccountId(),
                notificationId = command.notificationId(),
                deduplicationKey = command.deduplicationKey(),
                messageType = command.messageType(),
                title = command.title()?.trim()?.take(160),
                body = command.body()?.trim()?.take(320),
                dataPayload = payload,
                highPriority = command.highPriority(),
                ttlSeconds = ttlSeconds,
                collapseKey = command.collapseKey()?.trim()?.take(255)?.takeIf(String::isNotEmpty),
                now = now
            )
        }
    }

    private companion object {
        const val MAX_TTL_SECONDS = 2_419_200L
        const val MAX_DATA_BYTES = 3_500
    }
}
