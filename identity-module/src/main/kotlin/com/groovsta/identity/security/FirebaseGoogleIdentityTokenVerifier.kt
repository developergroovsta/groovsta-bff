package com.groovsta.identity.security

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.groovsta.identity.exception.InvalidExternalIdentityException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

@Component
@ConditionalOnProperty(prefix = "identity.auth.google", name = ["enabled"], havingValue = "true")
class FirebaseGoogleIdentityTokenVerifier(
    private val firebaseAuth: FirebaseAuth
) : GoogleIdentityTokenVerifier {

    override fun verify(idToken: String): VerifiedGoogleIdentity {
        logger.info(
            "google.sso.firebase.verify request tokenPresent={} tokenLength={}",
            idToken.isNotBlank(),
            idToken.length
        )
        val token = try {
            firebaseAuth.verifyIdToken(idToken.trim(), true)
        } catch (ex: FirebaseAuthException) {
            logger.warn(
                "google.sso.firebase.verify failed reason=firebase-auth-exception errorType={} message={}",
                ex.javaClass.simpleName,
                ex.message
            )
            throw InvalidExternalIdentityException()
        } catch (ex: IllegalArgumentException) {
            logger.warn(
                "google.sso.firebase.verify failed reason=invalid-token-argument errorType={} message={}",
                ex.javaClass.simpleName,
                ex.message
            )
            throw InvalidExternalIdentityException()
        } catch (ex: Exception) {
            logger.error(
                "google.sso.firebase.verify failed reason=unexpected-verification-error errorType={} message={}",
                ex.javaClass.name,
                ex.message,
                ex
            )
            throw InvalidExternalIdentityException()
        }

        val firebaseClaims = token.claims["firebase"] as? Map<*, *>
        val signInProvider = firebaseClaims?.get("sign_in_provider")
        if (signInProvider != GOOGLE_PROVIDER) {
            logger.warn(
                "google.sso.firebase.verify rejected reason=wrong-provider provider={}",
                signInProvider
            )
            throw InvalidExternalIdentityException()
        }
        val email = token.email?.trim()?.lowercase(Locale.ROOT)
        if (email.isNullOrBlank() || !token.isEmailVerified) {
            logger.warn(
                "google.sso.firebase.verify rejected reason=email-invalid subjectHash={} emailPresent={} emailVerified={}",
                token.uid?.let(::shortHash),
                !email.isNullOrBlank(),
                token.isEmailVerified
            )
            throw InvalidExternalIdentityException()
        }
        val subject = token.uid?.trim()
        if (subject.isNullOrBlank()) {
            logger.warn("google.sso.firebase.verify rejected reason=missing-subject emailHash={}", shortHash(email))
            throw InvalidExternalIdentityException()
        }
        logger.info(
            "google.sso.firebase.verify success subjectHash={} emailHash={} displayNamePresent={}",
            shortHash(subject),
            shortHash(email),
            !token.name.isNullOrBlank()
        )
        return VerifiedGoogleIdentity(
            subject = subject,
            email = email,
            displayName = token.name?.trim()?.takeIf(String::isNotEmpty)?.take(80)
        )
    }

    private fun shortHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(12)

    private companion object {
        private val logger = LoggerFactory.getLogger(FirebaseGoogleIdentityTokenVerifier::class.java)
        const val GOOGLE_PROVIDER = "google.com"
    }
}
