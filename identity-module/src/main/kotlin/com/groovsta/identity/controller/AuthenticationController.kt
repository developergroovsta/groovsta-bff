package com.groovsta.identity.controller

import com.groovsta.identity.dto.request.LoginRequest
import com.groovsta.identity.dto.request.ConfirmPasswordResetRequest
import com.groovsta.identity.dto.request.RequestPasswordResetRequest
import com.groovsta.identity.dto.request.SendEmailVerificationRequest
import com.groovsta.identity.dto.request.SendPhoneVerificationRequest
import com.groovsta.identity.dto.request.SignupRequest
import com.groovsta.identity.dto.request.VerifyCodeRequest
import com.groovsta.identity.dto.request.VerifyPhoneCodeRequest
import com.groovsta.identity.dto.response.AuthResponse
import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.model.DevicePlatform
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.service.AuthenticationService
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
class AuthenticationController(
    private val authenticationService: AuthenticationService,
    private val authenticatedAccountResolver: AuthenticatedAccountResolver
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account")
    fun signup(
        @Valid @RequestBody request: SignupRequest,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestHeader(name = "X-Platform", defaultValue = "UNKNOWN") platform: String,
        @RequestHeader(name = "X-App-Version", required = false) appVersion: String?,
        @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) userAgent: String?
    ): AuthResponse = authenticationService.signup(request, device(deviceId, platform, appVersion, userAgent))

    @PostMapping("/login")
    @Operation(summary = "Log in")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestHeader(name = "X-Platform", defaultValue = "UNKNOWN") platform: String,
        @RequestHeader(name = "X-App-Version", required = false) appVersion: String?,
        @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) userAgent: String?
    ): AuthResponse = authenticationService.login(request, device(deviceId, platform, appVersion, userAgent))

    @PostMapping("/forgot-password")
    @Operation(
        summary = "Request a password-reset OTP",
        description = "Provide exactly one verified contact: email or E.164 phone number."
    )
    fun requestPasswordReset(
        @Valid @RequestBody request: RequestPasswordResetRequest
    ) = authenticationService.requestPasswordReset(request)

    @PostMapping("/reset-password")
    @Operation(
        summary = "Reset a forgotten password",
        description = "Provide the same email or phone number used to request the OTP, the six-digit OTP, and a new password."
    )
    fun confirmPasswordReset(
        @Valid @RequestBody request: ConfirmPasswordResetRequest
    ): ResponseEntity<Void> {
        authenticationService.confirmPasswordReset(request)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun logout(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?
    ): ResponseEntity<Void> {
        val accountId = authenticatedAccountResolver.resolve(authorization)
        authenticationService.logout(accountId, authenticatedAccountResolver.token(authorization))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/email-verifications")
    @Operation(
        summary = "Request email verification",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun requestEmailVerification(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody(required = false) request: SendEmailVerificationRequest?
    ) =
        authenticationService.requestEmailVerification(authenticatedAccountResolver.resolve(authorization), request)

    @PostMapping("/email-verifications/confirm")
    @Operation(
        summary = "Confirm email verification",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun confirmEmailVerification(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: VerifyCodeRequest
    ) = authenticationService.confirmEmailVerification(authenticatedAccountResolver.resolve(authorization), request.code)

    @PostMapping("/phone-verifications")
    @Operation(
        summary = "Request phone verification",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun requestPhoneVerification(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: SendPhoneVerificationRequest
    ) = authenticationService.requestPhoneVerification(authenticatedAccountResolver.resolve(authorization), request)

    @PostMapping("/phone-verifications/confirm")
    @Operation(
        summary = "Confirm phone verification",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun confirmPhoneVerification(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: VerifyPhoneCodeRequest
    ) = authenticationService.confirmPhoneVerification(
        authenticatedAccountResolver.resolve(authorization),
        request.phoneNumber,
        request.code
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
