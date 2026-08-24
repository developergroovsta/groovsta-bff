package com.groovsta.identity.repository

import com.groovsta.identity.model.ExternalAuthProvider
import com.groovsta.identity.model.ExternalAuthRegistration
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface ExternalAuthRegistrationRepository : JpaRepository<ExternalAuthRegistration, UUID> {
    fun findAllByProviderAndProviderSubjectAndConsumedAtIsNull(
        provider: ExternalAuthProvider,
        providerSubject: String
    ): List<ExternalAuthRegistration>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from ExternalAuthRegistration registration where registration.tokenHash = :tokenHash")
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): Optional<ExternalAuthRegistration>
}
