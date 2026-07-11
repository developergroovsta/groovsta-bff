package com.groovsta.identity.dto.response

data class AuthResponse(
    val account: AccountResponse,
    val session: SessionResponse,
    val nextVerification: VerificationChallengeResponse?
)
