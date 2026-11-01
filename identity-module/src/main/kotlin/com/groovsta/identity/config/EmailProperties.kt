package com.groovsta.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "groovsta.email")
class EmailProperties {
    var enabled: Boolean = false
    var from: String = "groovstadeveloper@gmail.com"
    var replyTo: String = "groovstadeveloper@gmail.com"
}
