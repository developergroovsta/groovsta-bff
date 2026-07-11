package com.groovsta.identity.service.impl

import com.groovsta.identity.model.VerificationChannel
import com.groovsta.identity.service.VerificationDeliveryService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/** Local-only adapter. Production intentionally has no delivery bean until a real provider is configured. */
@Service
@Profile("!prod")
class LoggingVerificationDeliveryService : VerificationDeliveryService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(channel: VerificationChannel, destination: String, code: String) {
        log.warn("Development {} verification for {}: {}", channel, destination, code)
    }
}
