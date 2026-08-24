package com.groovsta.messaging.config

import com.groovsta.identity.security.AuthenticatedAccountResolver
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.security.Principal

@Configuration
@EnableWebSocketMessageBroker
class ChatWebSocketConfiguration(
    private val authenticatedAccountResolver: AuthenticatedAccountResolver
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/chat")
            .setAllowedOriginPatterns("*")
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
        registry.enableSimpleBroker("/queue")
        registry.setPreservePublishOrder(true)
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(object : ChannelInterceptor {
            override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
                val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
                    ?: return message
                if (accessor.command == StompCommand.CONNECT) {
                    val authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)
                    val accountId = authenticatedAccountResolver.resolve(authorization)
                    accessor.user = Principal { accountId.toString() }
                }
                return message
            }
        })
    }
}
