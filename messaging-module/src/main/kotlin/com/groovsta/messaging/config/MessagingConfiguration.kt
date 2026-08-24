package com.groovsta.messaging.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.core.env.Environment
import java.time.Duration

@Configuration
@EntityScan(basePackages = ["com.groovsta.messaging.model"])
@EnableJpaRepositories(basePackages = ["com.groovsta.messaging.repository"])
@EnableConfigurationProperties(ChatMediaProperties::class)
@EnableScheduling
class MessagingConfiguration {
    @Bean
    fun callProperties(environment: Environment) = CallProperties(
        maxParticipants = environment.getProperty("GROOVSTA_CALL_MAX_PARTICIPANTS", Int::class.java, 8) ?: 8,
        ringTimeout = environment.getProperty("GROOVSTA_CALL_RING_TIMEOUT", Duration::class.java, Duration.ofSeconds(30))
            ?: Duration.ofSeconds(30),
        heartbeatTimeout = environment.getProperty("GROOVSTA_CALL_HEARTBEAT_TIMEOUT", Duration::class.java, Duration.ofSeconds(90))
            ?: Duration.ofSeconds(90),
        cleanupInterval = environment.getProperty("GROOVSTA_CALL_CLEANUP_INTERVAL", Duration::class.java, Duration.ofSeconds(10))
            ?: Duration.ofSeconds(10),
        stunUrls = parseList(environment.getProperty("GROOVSTA_CALL_STUN_URLS", "stun:stun.l.google.com:19302")),
        turnUrls = parseList(environment.getProperty("GROOVSTA_CALL_TURN_URLS", LOCAL_TURN_URLS)),
        turnUsername = environment.getProperty("GROOVSTA_CALL_TURN_USERNAME", LOCAL_TURN_USERNAME),
        turnCredential = environment.getProperty("GROOVSTA_CALL_TURN_CREDENTIAL", LOCAL_TURN_CREDENTIAL)
    )

    private fun parseList(value: String): List<String> =
        value.split(",").map(String::trim).filter(String::isNotEmpty)

    companion object {
        private const val LOCAL_TURN_URLS =
            "turn:192.168.0.161:3478?transport=udp,turn:192.168.0.161:3478?transport=tcp"
        private const val LOCAL_TURN_USERNAME = "groovsta"
        private const val LOCAL_TURN_CREDENTIAL = "replace-with-a-local-development-secret"
    }
}
