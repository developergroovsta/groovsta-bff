package com.groovsta.identity.security

import com.groovsta.identity.exception.InvalidCredentialsException
import com.groovsta.identity.service.AuthenticationService
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AuthenticatedAccountResolver(private val authenticationService: AuthenticationService) {

    fun resolve(authorization: String?): UUID {
        if (authorization == null || !authorization.startsWith("Bearer ", ignoreCase = true)) {
            throw InvalidCredentialsException()
        }
        val token = authorization.substringAfter(' ').trim()
        if (token.isBlank()) {
            throw InvalidCredentialsException()
        }
        return authenticationService.authenticate(token)
    }

    fun token(authorization: String?): String {
        if (authorization == null || !authorization.startsWith("Bearer ", ignoreCase = true)) {
            throw InvalidCredentialsException()
        }
        return authorization.substringAfter(' ').trim().ifBlank { throw InvalidCredentialsException() }
    }
}
