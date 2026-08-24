package com.groovsta.notification.service

import com.groovsta.notification.config.PushProperties
import com.groovsta.notification.repository.PushDeliveryClaimRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class PushDeliveryScheduler(
    private val claimRepository: PushDeliveryClaimRepository,
    private val processor: PushDeliveryProcessor,
    private val properties: PushProperties
) {
    @Scheduled(fixedDelayString = "\${groovsta.push.poll-interval:2000}")
    fun deliver() {
        val now = Instant.now()
        claimRepository.claim(
            now = now,
            lockedUntil = now.plus(properties.leaseDuration),
            limit = properties.batchSize.coerceIn(1, 500)
        ).forEach(processor::process)
    }
}
