package com.groovsta.identity.controller

import com.groovsta.identity.dto.request.CompleteGoogleSignupRequest
import com.groovsta.identity.dto.request.GoogleAuthenticationRequest
import com.groovsta.identity.dto.response.AuthResponse
import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.dto.response.GoogleAuthenticationResponse
import com.groovsta.identity.model.DevicePlatform
import com.groovsta.identity.service.GoogleAuthenticationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

@RestController
@RequestMapping("/api/v1/auth/google")
@Tag(name = "Authentication")
class GoogleAuthenticationController(
    private val googleAuthenticationService: GoogleAuthenticationService
) {

    @PostMapping
    @Operation(
        summary = "Sign in with Google",
        description = "Exchange a Firebase ID token created through the google.com provider for a Groovsta session."
    )
    fun authenticate(
        @Valid @RequestBody request: GoogleAuthenticationRequest,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestHeader(name = "X-Platform", defaultValue = "UNKNOWN") platform: String,
        @RequestHeader(name = "X-App-Version", required = false) appVersion: String?,
        @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) userAgent: String?
    ): GoogleAuthenticationResponse = googleAuthenticationService.authenticate(
        request,
        device(deviceId, platform, appVersion, userAgent)
    )

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Complete Google signup",
        description = "Create the profile for a first-time Google user using the short-lived registration token."
    )
    fun completeSignup(
        @Valid @RequestBody request: CompleteGoogleSignupRequest,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestHeader(name = "X-Platform", defaultValue = "UNKNOWN") platform: String,
        @RequestHeader(name = "X-App-Version", required = false) appVersion: String?,
        @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) userAgent: String?
    ): AuthResponse = googleAuthenticationService.completeSignup(
        request,
        device(deviceId, platform, appVersion, userAgent)
    )

    private fun device(deviceId: String, platform: String, appVersion: String?, userAgent: String?) = DeviceMetadata(
        deviceIdentifier = deviceId,
        platform = DevicePlatform.entries.firstOrNull {
            it.name == platform.trim().uppercase(Locale.ROOT)
        } ?: DevicePlatform.UNKNOWN,
        appVersion = appVersion,
        userAgent = userAgent
    )
}
