package com.groovsta.identity.repository

import com.groovsta.identity.model.Profile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface ProfileRepository : JpaRepository<Profile, UUID> {
    fun findByAccountId(accountId: UUID): Optional<Profile>
    fun findByHandle(handle: String): Optional<Profile>
    fun existsByHandle(handle: String): Boolean
}
