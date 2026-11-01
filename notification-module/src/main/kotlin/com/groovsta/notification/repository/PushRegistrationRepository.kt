package com.groovsta.notification.repository

import com.groovsta.notification.model.PushRegistration
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.util.UUID

interface PushRegistrationRepository : JpaRepository<PushRegistration, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdentityDeviceId(identityDeviceId: UUID): PushRegistration?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByRegistrationIdAndDisabledAtIsNull(registrationId: String): PushRegistration?

    fun findByAccountIdAndDisabledAtIsNull(accountId: UUID): List<PushRegistration>
}
