package com.groovsta.identity.repository

import com.groovsta.identity.model.Account
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByEmail(email: String): Optional<Account>
    fun findByPhoneNumber(phoneNumber: String): Optional<Account>
    fun existsByEmail(email: String): Boolean
    fun existsByPhoneNumber(phoneNumber: String): Boolean
}
