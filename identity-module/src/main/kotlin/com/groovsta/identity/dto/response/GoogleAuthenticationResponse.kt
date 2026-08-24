package com.groovsta.identity.dto.response

import java.time.Instant

data class GoogleAuthenticationResponse(
    val status: GoogleAuthenticationStatus,
    val account: AccountResponse? = null,
    val session: SessionResponse? = null,
    val nextVerification: VerificationChallengeResponse? = null,
    val registrationToken: String? = null,
    val registrationExpiresAt: Instant? = null,
    val email: String? = null,
    val suggestedDisplayName: String? = null
)
