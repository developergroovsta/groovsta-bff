package com.groovsta.identity.security

interface GoogleIdentityTokenVerifier {
    fun verify(idToken: String): VerifiedGoogleIdentity
}
