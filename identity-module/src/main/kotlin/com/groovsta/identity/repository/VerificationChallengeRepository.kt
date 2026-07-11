package com.groovsta.identity.repository

import com.groovsta.identity.model.VerificationChallenge
import com.groovsta.identity.model.VerificationChannel
import com.groovsta.identity.model.VerificationPurpose
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface VerificationChallengeRepository : JpaRepository<VerificationChallenge, UUID> {
    fun findByAccountIdAndPurposeAndChannelAndDestinationAndVerifiedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
        accountId: UUID,
        purpose: VerificationPurpose,
        channel: VerificationChannel,
        destination: String
    ): List<VerificationChallenge>
}
