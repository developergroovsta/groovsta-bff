package com.groovsta.identity.service

import com.groovsta.identity.model.VerificationChannel

/** Implement this with SES and Twilio before enabling the prod profile. */
interface VerificationDeliveryService {
    fun send(channel: VerificationChannel, destination: String, code: String)
}
