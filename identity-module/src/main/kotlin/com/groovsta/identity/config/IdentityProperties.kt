package com.groovsta.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "identity.auth")
class IdentityProperties {
    var sessionTtl: Duration = Duration.ofDays(30)
    var emailVerificationTtl: Duration = Duration.ofMinutes(15)
    var phoneVerificationTtl: Duration = Duration.ofMinutes(10)
    var maxVerificationAttempts: Int = 5
    var verificationPepper: String = ""
    var google: GoogleAuthenticationProperties = GoogleAuthenticationProperties()

    class GoogleAuthenticationProperties {
        var enabled: Boolean = false
        var registrationTtl: Duration = Duration.ofMinutes(10)
    }
}
