package com.groovsta.identity.service.impl

import com.groovsta.identity.config.IdentityProperties
import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.dto.response.SessionResponse
import com.groovsta.identity.model.Device
import com.groovsta.identity.model.Session
import com.groovsta.identity.repository.DeviceRepository
import com.groovsta.identity.repository.SessionRepository
import com.groovsta.identity.service.IdentitySessionService
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class IdentitySessionServiceImpl(
    private val deviceRepository: DeviceRepository,
    private val sessionRepository: SessionRepository,
    private val properties: IdentityProperties
) : IdentitySessionService {

    private val secureRandom = SecureRandom()

    override fun create(accountId: UUID, device: DeviceMetadata): SessionResponse {
        val deviceRecord = registerDevice(accountId, device)
        val rawToken = generateAccessToken()
        val session = sessionRepository.save(
            Session(
                accountId = accountId,
                deviceId = deviceRecord.id,
                tokenHash = hash(rawToken),
                expiresAt = Instant.now().plus(properties.sessionTtl)
            )
        )
        return SessionResponse(rawToken, "Bearer", session.expiresAt)
    }

    override fun hash(accessToken: String): String = MessageDigest.getInstance("SHA-256")
        .digest(accessToken.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun registerDevice(accountId: UUID, device: DeviceMetadata): Device {
        val identifier = device.deviceIdentifier.trim()
        require(identifier.isNotEmpty()) { "X-Device-Id is required" }
        return deviceRepository.findByAccountIdAndDeviceIdentifier(accountId, identifier)
            .orElse(null)
            ?.also { it.seen(device.platform, device.appVersion?.trim(), device.userAgent?.take(1024)) }
            ?: deviceRepository.save(
                Device(
                    accountId = accountId,
                    deviceIdentifier = identifier,
                    platform = device.platform,
                    appVersion = device.appVersion?.trim(),
                    lastUserAgent = device.userAgent?.take(1024)
                )
            )
    }

    private fun generateAccessToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
