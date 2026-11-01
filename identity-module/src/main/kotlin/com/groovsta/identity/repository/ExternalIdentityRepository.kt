package com.groovsta.identity.repository

import com.groovsta.identity.model.ExternalAuthProvider
import com.groovsta.identity.model.ExternalIdentity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface ExternalIdentityRepository : JpaRepository<ExternalIdentity, UUID> {
    fun findByProviderAndProviderSubject(
        provider: ExternalAuthProvider,
        providerSubject: String
    ): Optional<ExternalIdentity>

    fun findByAccountIdAndProvider(
        accountId: UUID,
        provider: ExternalAuthProvider
    ): Optional<ExternalIdentity>
}
