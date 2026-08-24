package com.groovsta.identity.service.impl

import com.groovsta.identity.dto.request.CompleteGoogleSignupRequest
import com.groovsta.identity.dto.request.GoogleAuthenticationRequest
import com.groovsta.identity.dto.response.AuthResponse
import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.dto.response.GoogleAuthenticationResponse
import com.groovsta.identity.service.GoogleAuthenticationService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
@ConditionalOnProperty(
    prefix = "identity.auth.google",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true
)
class DisabledGoogleAuthenticationService : GoogleAuthenticationService {

    override fun authenticate(
        request: GoogleAuthenticationRequest,
        device: DeviceMetadata
    ): GoogleAuthenticationResponse {
        logger.warn(
            "google.sso.disabled authenticate request deviceIdPresent={} platform={} tokenLength={}",
            device.deviceIdentifier.isNotBlank(),
            device.platform,
            request.idToken.length
        )
        throw disabled()
    }

    override fun completeSignup(
        request: CompleteGoogleSignupRequest,
        device: DeviceMetadata
    ): AuthResponse {
        logger.warn(
            "google.sso.disabled complete-signup request registrationTokenPresent={} platform={}",
            request.registrationToken.isNotBlank(),
            device.platform
        )
        throw disabled()
    }

    private fun disabled() = ResponseStatusException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Google authentication is disabled. Set GROOVSTA_GOOGLE_AUTH_ENABLED=true and restart the backend."
    )

    private companion object {
        private val logger = LoggerFactory.getLogger(DisabledGoogleAuthenticationService::class.java)
    }
}
