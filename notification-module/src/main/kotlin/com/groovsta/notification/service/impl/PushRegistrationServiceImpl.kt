package com.groovsta.notification.service.impl

import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.model.Device
import com.groovsta.identity.model.DevicePlatform
import com.groovsta.identity.repository.DeviceRepository
import com.groovsta.notification.dto.PushRegistrationResponse
import com.groovsta.notification.dto.RegisterPushTokenRequest
import com.groovsta.notification.model.PushRegistration
import com.groovsta.notification.repository.PushDeliveryRepository
import com.groovsta.notification.repository.PushRegistrationRepository
import com.groovsta.notification.service.PushRegistrationService
import com.groovsta.shared.notification.PushTokenRevoker
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PushRegistrationServiceImpl(
    private val registrationRepository: PushRegistrationRepository,
    private val deliveryRepository: PushDeliveryRepository,
    private val deviceRepository: DeviceRepository
) : PushRegistrationService, PushTokenRevoker {

    @Transactional
    override fun register(
        accountId: UUID,
        deviceIdentifier: String,
        request: RegisterPushTokenRequest
    ): PushRegistrationResponse {
        val device = ownedDevice(accountId, deviceIdentifier)
        if (device.platform == DevicePlatform.UNKNOWN) {
            throw IllegalArgumentException("Push registration requires ANDROID, IOS, or WEB device platform")
        }
        val registrationId = request.registrationId.trim()
        val claimedByOtherDevice = registrationRepository
            .findFirstByRegistrationIdAndDisabledAtIsNull(registrationId)
            ?.takeIf { it.identityDeviceId != device.id }
        claimedByOtherDevice?.let {
            disable(it, "Registration moved to another authenticated device")
            registrationRepository.flush()
        }

        val registration = registrationRepository.findByIdentityDeviceId(device.id)
            ?.also {
                it.accountId = accountId
                it.register(registrationId, request.identifierType, device.platform)
            }
            ?: PushRegistration(
                accountId = accountId,
                identityDeviceId = device.id,
                registrationId = registrationId,
                identifierType = request.identifierType,
                platform = device.platform
            )
        return response(registrationRepository.saveAndFlush(registration), device)
    }

    @Transactional
    override fun unregister(accountId: UUID, deviceIdentifier: String) {
        val device = ownedDevice(accountId, deviceIdentifier)
        registrationRepository.findByIdentityDeviceId(device.id)?.let {
            if (it.accountId != accountId) return@let
            disable(it, "Unregistered by client")
        }
    }

    @Transactional
    override fun disableDevice(identityDeviceId: UUID) {
        registrationRepository.findByIdentityDeviceId(identityDeviceId)?.let {
            disable(it, "Authentication session logged out")
        }
    }

    @Transactional
    override fun disableAccount(accountId: UUID) {
        registrationRepository.findByAccountIdAndDisabledAtIsNull(accountId).forEach {
            disable(it, "All authentication sessions were revoked")
        }
    }

    private fun disable(registration: PushRegistration, reason: String) {
        registration.disable()
        registrationRepository.save(registration)
        deliveryRepository.skipOpenByRegistrationId(registration.id, reason.take(1000), Instant.now())
    }

    private fun ownedDevice(accountId: UUID, deviceIdentifier: String): Device {
        val normalized = deviceIdentifier.trim()
        if (normalized.isEmpty()) throw IllegalArgumentException("X-Device-Id is required")
        return deviceRepository.findByAccountIdAndDeviceIdentifier(accountId, normalized)
            .orElseThrow { IdentityNotFoundException("Authenticated device was not found; log in again") }
    }

    private fun response(registration: PushRegistration, device: Device) = PushRegistrationResponse(
        id = registration.id,
        deviceIdentifier = device.deviceIdentifier,
        platform = registration.platform,
        identifierType = registration.identifierType,
        active = registration.disabledAt == null,
        registeredAt = registration.updatedAt
    )
}
