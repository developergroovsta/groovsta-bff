package com.groovsta.identity.dto.response

import com.groovsta.identity.model.DevicePlatform

data class DeviceMetadata(
    val deviceIdentifier: String,
    val platform: DevicePlatform,
    val appVersion: String?,
    val userAgent: String?
)
