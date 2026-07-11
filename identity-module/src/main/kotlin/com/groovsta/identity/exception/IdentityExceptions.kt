package com.groovsta.identity.exception

class IdentityConflictException(message: String) : RuntimeException(message)

class IdentityNotFoundException(message: String) : RuntimeException(message)

class InvalidCredentialsException : RuntimeException("Invalid email, password, or session token")

class InvalidVerificationException(message: String) : RuntimeException(message)
