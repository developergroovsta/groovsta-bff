package com.groovsta.identity.repository

import com.groovsta.identity.model.Session
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface SessionRepository : JpaRepository<Session, UUID> {
    fun findByTokenHash(tokenHash: String): Optional<Session>
    fun findAllByAccountId(accountId: UUID): List<Session>
}
