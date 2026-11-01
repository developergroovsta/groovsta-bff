package com.groovsta.identity.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "identity_devices")
class Device(
    id: UUID = UUID.randomUUID(),
    @Column(name = "account_id", nullable = false)
    var accountId: UUID = UUID.randomUUID(),
    @Column(name = "device_identifier", nullable = false, length = 255)
    var deviceIdentifier: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var platform: DevicePlatform = DevicePlatform.UNKNOWN,
    @Column(name = "app_version", length = 64)
    var appVersion: String? = null,
    @Column(name = "last_user_agent", length = 1024)
    var lastUserAgent: String? = null,
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    var firstSeenAt: Instant = Instant.EPOCH,
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.EPOCH
) : AbstractModel(id) {

    @PrePersist
    fun onCreate() {
        val now = Instant.now()
        firstSeenAt = now
        lastSeenAt = now
    }

    fun seen(platform: DevicePlatform, appVersion: String?, userAgent: String?) {
        this.platform = platform
        this.appVersion = appVersion
        lastUserAgent = userAgent
        lastSeenAt = Instant.now()
    }
}
