package com.groovsta.messaging.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CallCleanupScheduler(
    private val callService: CallService
) {
    @Scheduled(fixedDelayString = "\${groovsta.calls.cleanup-interval:10000}")
    fun cleanup() {
        callService.expireStaleCalls()
    }
}
