package com.groovsta.notification.service

import com.groovsta.notification.config.PushProperties
import com.groovsta.notification.model.PushDeliveryStatus
import com.groovsta.notification.repository.PushDeliveryRepository
import com.groovsta.notification.repository.PushRegistrationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

@Service
class PushDeliveryOutcomeService(
    private val deliveryRepository: PushDeliveryRepository,
    private val registrationRepository: PushRegistrationRepository,
    private val properties: PushProperties
) {
    @Transactional
    fun complete(deliveryId: UUID, result: PushSendResult) {
        val delivery = deliveryRepository.findById(deliveryId).orElse(null) ?: return
        if (delivery.status != PushDeliveryStatus.PROCESSING) return
        val registration = registrationRepository.findById(delivery.pushRegistrationId).orElse(null)
        val now = Instant.now()
        delivery.lockedUntil = null
        delivery.updatedAt = now
        when (result) {
            is PushSendResult.Sent -> {
                delivery.status = PushDeliveryStatus.SENT
                delivery.sentAt = now
                delivery.providerMessageId = result.providerMessageId.take(512)
                delivery.lastError = null
                registration?.recordSuccess()
            }
            is PushSendResult.Skipped -> {
                delivery.status = PushDeliveryStatus.SKIPPED
                delivery.lastError = result.reason.take(1000)
            }
            is PushSendResult.PermanentFailure -> {
                delivery.status = PushDeliveryStatus.DEAD
                delivery.lastError = result.reason.take(1000)
                registration?.recordFailure(now)
                deliveryRepository.saveAndFlush(delivery)
                if (result.invalidateRegistration && registration != null) {
                    registration.disable(now)
                    registrationRepository.save(registration)
                    deliveryRepository.skipOpenByRegistrationId(
                        registration.id,
                        "FCM registration is no longer valid",
                        now
                    )
                }
                return
            }
            is PushSendResult.Retryable -> {
                registration?.recordFailure(now)
                delivery.lastError = result.reason.take(1000)
                if (delivery.attemptCount >= properties.maxAttempts.coerceAtLeast(1)) {
                    delivery.status = PushDeliveryStatus.DEAD
                } else {
                    delivery.status = PushDeliveryStatus.RETRY
                    delivery.nextAttemptAt = now.plus(retryDelay(delivery.attemptCount))
                }
            }
        }
        deliveryRepository.save(delivery)
        registration?.let(registrationRepository::save)
    }

    private fun retryDelay(attempt: Int): Duration {
        val multiplier = 1L shl (attempt - 1).coerceIn(0, 20)
        val baseMillis = properties.initialRetryDelay.toMillis().coerceAtLeast(1)
        val cappedMillis = (baseMillis * multiplier)
            .coerceAtMost(properties.maxRetryDelay.toMillis().coerceAtLeast(baseMillis))
        val jitter = ThreadLocalRandom.current().nextLong((cappedMillis / 5).coerceAtLeast(1))
        return Duration.ofMillis(cappedMillis + jitter)
    }
}
