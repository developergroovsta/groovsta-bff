package com.groovsta.identity.service.impl

import com.groovsta.identity.model.VerificationChannel
import com.groovsta.identity.model.VerificationPurpose
import com.groovsta.identity.service.VerificationDeliveryService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/** Local-only adapter. Production intentionally has no delivery bean until a real provider is configured. */
@Service
@Profile("!prod")
@ConditionalOnProperty(name = ["groovsta.email.enabled"], havingValue = "false", matchIfMissing = true)
class LoggingVerificationDeliveryService : VerificationDeliveryService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(
        channel: VerificationChannel,
        destination: String,
        code: String,
        purpose: VerificationPurpose
    ) {
        log.warn("Development {} {} verification for {}: {}", purpose, channel, destination, code)
    }
}
