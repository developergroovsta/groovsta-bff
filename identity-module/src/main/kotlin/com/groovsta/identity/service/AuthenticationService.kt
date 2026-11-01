package com.groovsta.identity.service

import com.groovsta.identity.dto.request.ConfirmPasswordResetRequest
import com.groovsta.identity.dto.request.LoginRequest
import com.groovsta.identity.dto.request.RequestPasswordResetRequest
import com.groovsta.identity.dto.request.SendEmailVerificationRequest
import com.groovsta.identity.dto.request.SendPhoneVerificationRequest
import com.groovsta.identity.dto.request.SignupRequest
import com.groovsta.identity.dto.response.AccountResponse
import com.groovsta.identity.dto.response.AuthResponse
import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.dto.response.VerificationChallengeResponse
import java.util.UUID

interface AuthenticationService {
    fun signup(request: SignupRequest, device: DeviceMetadata): AuthResponse
    fun login(request: LoginRequest, device: DeviceMetadata): AuthResponse
    fun authenticate(accessToken: String): UUID
    fun logout(accountId: UUID, accessToken: String)
    fun requestEmailVerification(accountId: UUID, request: SendEmailVerificationRequest?): VerificationChallengeResponse
    fun confirmEmailVerification(accountId: UUID, code: String): AccountResponse
    fun requestPhoneVerification(accountId: UUID, request: SendPhoneVerificationRequest): VerificationChallengeResponse
    fun confirmPhoneVerification(accountId: UUID, phoneNumber: String, code: String): AccountResponse
    fun requestPasswordReset(request: RequestPasswordResetRequest): VerificationChallengeResponse
    fun confirmPasswordReset(request: ConfirmPasswordResetRequest)
}
