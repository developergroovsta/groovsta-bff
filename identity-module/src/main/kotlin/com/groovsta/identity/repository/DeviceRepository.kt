package com.groovsta.identity.repository

import com.groovsta.identity.model.Device
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface DeviceRepository : JpaRepository<Device, UUID> {
    fun findByAccountIdAndDeviceIdentifier(accountId: UUID, deviceIdentifier: String): Optional<Device>
}
