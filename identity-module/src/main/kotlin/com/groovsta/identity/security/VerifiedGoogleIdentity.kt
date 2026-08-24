package com.groovsta.identity.security

data class VerifiedGoogleIdentity(
    val subject: String,
    val email: String,
    val displayName: String?
)
