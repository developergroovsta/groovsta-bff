package com.groovsta.content.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("groovsta.locations")
data class LocationProperties(
    var providerEnabled: Boolean = false,
    var providerBaseUrl: String = "https://api.olamaps.io",
    var providerApiKey: String = "",
    var providerOrigin: String = "",
    var language: String = "en",
    var searchMinLength: Int = 3,
    var searchCacheTtl: Duration = Duration.ofDays(7),
    var reverseCacheTtl: Duration = Duration.ofDays(30),
    var searchRadiusMeters: Int = 50_000,
    var searchLimit: Int = 20,
    var providerRateLimitPerMinute: Int = 30
)
