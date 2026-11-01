package com.groovsta.identity.repository

import com.groovsta.identity.model.Account
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByEmail(email: String): Optional<Account>
    fun findByPhoneNumber(phoneNumber: String): Optional<Account>
    fun existsByEmail(email: String): Boolean
    fun existsByPhoneNumber(phoneNumber: String): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.email = :email")
    fun findByEmailForUpdate(@Param("email") email: String): Optional<Account>
}
