package com.groovsta.identity.service.impl

import com.groovsta.identity.config.IdentityProperties
import com.groovsta.identity.dto.request.CompleteGoogleSignupRequest
import com.groovsta.identity.dto.request.GoogleAuthenticationRequest
import com.groovsta.identity.dto.response.AuthResponse
import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.dto.response.GoogleAuthenticationResponse
import com.groovsta.identity.dto.response.GoogleAuthenticationStatus
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.exception.InvalidExternalIdentityException
import com.groovsta.identity.exception.InvalidExternalRegistrationException
import com.groovsta.identity.model.Account
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.ExternalAuthProvider
import com.groovsta.identity.model.ExternalAuthRegistration
import com.groovsta.identity.model.ExternalIdentity
import com.groovsta.identity.model.Profile
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.ExternalAuthRegistrationRepository
import com.groovsta.identity.repository.ExternalIdentityRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.security.GoogleIdentityTokenVerifier
import com.groovsta.identity.security.VerifiedGoogleIdentity
import com.groovsta.identity.service.GoogleAuthenticationService
import com.groovsta.identity.service.IdentitySessionService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale

@Service
@ConditionalOnProperty(prefix = "identity.auth.google", name = ["enabled"], havingValue = "true")
class GoogleAuthenticationServiceImpl(
    private val tokenVerifier: GoogleIdentityTokenVerifier,
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val externalIdentityRepository: ExternalIdentityRepository,
    private val registrationRepository: ExternalAuthRegistrationRepository,
    private val identitySessionService: IdentitySessionService,
    private val accountResponseAssembler: AccountResponseAssembler,
    private val properties: IdentityProperties,
    private val transactionTemplate: TransactionTemplate
) : GoogleAuthenticationService {

    private val secureRandom = SecureRandom()

    override fun authenticate(
        request: GoogleAuthenticationRequest,
        device: DeviceMetadata
    ): GoogleAuthenticationResponse {
        logger.info(
            "google.sso.authenticate request deviceIdPresent={} platform={} appVersionPresent={} tokenLength={}",
            device.deviceIdentifier.isNotBlank(),
            device.platform,
            !device.appVersion.isNullOrBlank(),
            request.idToken.length
        )
        val verifiedIdentity = runCatching {
            tokenVerifier.verify(request.idToken)
        }.onFailure { ex ->
            logger.warn(
                "google.sso.authenticate failed stage=token-verification errorType={} message={}",
                ex.javaClass.simpleName,
                ex.message
            )
        }.getOrThrow()
        logger.info(
            "google.sso.authenticate token-verified subjectHash={} emailHash={} displayNamePresent={}",
            shortHash(verifiedIdentity.subject),
            shortHash(verifiedIdentity.email),
            !verifiedIdentity.displayName.isNullOrBlank()
        )
        val response = transactionTemplate.execute {
            authenticateVerified(verifiedIdentity, device)
        } ?: error("Google authentication transaction did not return a response")
        logger.info(
            "google.sso.authenticate response status={} accountId={} onboardingRequired={}",
            response.status,
            response.account?.id,
            response.status == GoogleAuthenticationStatus.ONBOARDING_REQUIRED
        )
        return response
    }

    override fun completeSignup(
        request: CompleteGoogleSignupRequest,
        device: DeviceMetadata
    ): AuthResponse = transactionTemplate.execute {
        logger.info(
            "google.sso.complete-signup request registrationTokenPresent={} handle={} displayNamePresent={} " +
                "adultDeclaration={} deviceIdPresent={} platform={}",
            request.registrationToken.isNotBlank(),
            normalizeHandle(request.handle),
            request.displayName.isNotBlank(),
            request.adultDeclaration,
            device.deviceIdentifier.isNotBlank(),
            device.platform
        )
        if (!request.adultDeclaration) {
            logger.warn("google.sso.complete-signup rejected reason=adult-declaration-required")
            throw IllegalArgumentException("An 18+ declaration is required")
        }
        val now = Instant.now()
        val registrationTokenHash = hash(request.registrationToken.trim())
        val registration = registrationRepository.findByTokenHashForUpdate(registrationTokenHash)
            .orElseThrow {
                logger.warn(
                    "google.sso.complete-signup rejected reason=registration-not-found tokenHashPrefix={}",
                    registrationTokenHash.take(12)
                )
                InvalidExternalRegistrationException("Google signup registration is invalid or has expired")
            }
        if (!registration.isOpenAt(now)) {
            logger.warn(
                "google.sso.complete-signup rejected reason=registration-not-open registrationId={} " +
                    "expiresAt={} consumed={}",
                registration.id,
                registration.expiresAt,
                registration.consumedAt != null
            )
            throw InvalidExternalRegistrationException("Google signup registration is invalid or has expired")
        }

        val existingIdentity = externalIdentityRepository.findByProviderAndProviderSubject(
            registration.provider,
            registration.providerSubject
        ).orElse(null)
        if (existingIdentity != null) {
            val account = accountRepository.findById(existingIdentity.accountId)
                .orElseThrow { InvalidExternalIdentityException() }
            registration.complete(account.id, now)
            logger.info(
                "google.sso.complete-signup existing-identity accountId={} registrationId={}",
                account.id,
                registration.id
            )
            return@execute authenticated(account, existingIdentity, registration.email, device, now).toAuthResponse()
        }

        val accountWithEmail = accountRepository.findByEmailForUpdate(registration.email).orElse(null)
        if (accountWithEmail != null) {
            val identity = linkIdentity(accountWithEmail, registration.providerSubject, registration.email, now)
            registration.complete(accountWithEmail.id, now)
            logger.info(
                "google.sso.complete-signup linked-existing-email accountId={} registrationId={}",
                accountWithEmail.id,
                registration.id
            )
            return@execute authenticated(accountWithEmail, identity, registration.email, device, now).toAuthResponse()
        }

        val handle = normalizeHandle(request.handle)
        if (profileRepository.existsByHandle(handle)) {
            logger.warn("google.sso.complete-signup rejected reason=handle-conflict handle={}", handle)
            throw IdentityConflictException("This handle is already in use")
        }
        val account = accountRepository.save(
            Account(
                email = registration.email,
                passwordHash = null,
                emailVerifiedAt = now,
                adultDeclaredAt = now
            )
        )
        profileRepository.save(
            Profile(
                accountId = account.id,
                handle = handle,
                displayName = request.displayName.trim()
            )
        )
        val identity = linkIdentity(account, registration.providerSubject, registration.email, now)
        registration.complete(account.id, now)
        logger.info(
            "google.sso.complete-signup created-account accountId={} registrationId={} handle={}",
            account.id,
            registration.id,
            handle
        )
        authenticated(account, identity, registration.email, device, now).toAuthResponse()
    } ?: error("Google signup transaction did not return a response")

    private fun authenticateVerified(
        verifiedIdentity: VerifiedGoogleIdentity,
        device: DeviceMetadata
    ): GoogleAuthenticationResponse {
        val now = Instant.now()
        val linkedIdentity = externalIdentityRepository.findByProviderAndProviderSubject(
            ExternalAuthProvider.GOOGLE,
            verifiedIdentity.subject
        ).orElse(null)
        if (linkedIdentity != null) {
            logger.info(
                "google.sso.authenticate branch=linked-identity accountId={} subjectHash={}",
                linkedIdentity.accountId,
                shortHash(verifiedIdentity.subject)
            )
            val account = accountRepository.findById(linkedIdentity.accountId)
                .orElseThrow { InvalidExternalIdentityException() }
            val emailOwner = accountRepository.findByEmail(verifiedIdentity.email).orElse(null)
            if (emailOwner != null && emailOwner.id != account.id) {
                logger.warn(
                    "google.sso.authenticate rejected reason=email-owned-by-different-account linkedAccountId={} " +
                        "emailOwnerAccountId={}",
                    account.id,
                    emailOwner.id
                )
                throw InvalidExternalIdentityException()
            }
            return authenticated(account, linkedIdentity, verifiedIdentity.email, device, now)
        }

        val accountWithEmail = accountRepository.findByEmailForUpdate(verifiedIdentity.email).orElse(null)
        if (accountWithEmail != null) {
            logger.info(
                "google.sso.authenticate branch=existing-email accountId={} subjectHash={}",
                accountWithEmail.id,
                shortHash(verifiedIdentity.subject)
            )
            val concurrentlyLinked = externalIdentityRepository.findByProviderAndProviderSubject(
                ExternalAuthProvider.GOOGLE,
                verifiedIdentity.subject
            ).orElse(null)
            if (concurrentlyLinked != null) {
                val linkedAccount = accountRepository.findById(concurrentlyLinked.accountId)
                    .orElseThrow { InvalidExternalIdentityException() }
                logger.info(
                    "google.sso.authenticate branch=concurrently-linked accountId={} subjectHash={}",
                    linkedAccount.id,
                    shortHash(verifiedIdentity.subject)
                )
                return authenticated(linkedAccount, concurrentlyLinked, verifiedIdentity.email, device, now)
            }
            val identity = linkIdentity(accountWithEmail, verifiedIdentity.subject, verifiedIdentity.email, now)
            return authenticated(accountWithEmail, identity, verifiedIdentity.email, device, now)
        }

        registrationRepository.findAllByProviderAndProviderSubjectAndConsumedAtIsNull(
            ExternalAuthProvider.GOOGLE,
            verifiedIdentity.subject
        ).forEach { it.invalidate(now) }

        val rawRegistrationToken = generateRegistrationToken()
        val expiresAt = now.plus(properties.google.registrationTtl)
        registrationRepository.save(
            ExternalAuthRegistration(
                provider = ExternalAuthProvider.GOOGLE,
                providerSubject = verifiedIdentity.subject,
                email = verifiedIdentity.email,
                suggestedDisplayName = verifiedIdentity.displayName,
                tokenHash = hash(rawRegistrationToken),
                expiresAt = expiresAt
            )
        )
        logger.info(
            "google.sso.authenticate branch=onboarding-required subjectHash={} emailHash={} expiresAt={}",
            shortHash(verifiedIdentity.subject),
            shortHash(verifiedIdentity.email),
            expiresAt
        )
        return GoogleAuthenticationResponse(
            status = GoogleAuthenticationStatus.ONBOARDING_REQUIRED,
            registrationToken = rawRegistrationToken,
            registrationExpiresAt = expiresAt,
            email = verifiedIdentity.email,
            suggestedDisplayName = verifiedIdentity.displayName
        )
    }

    private fun linkIdentity(
        account: Account,
        providerSubject: String,
        providerEmail: String,
        now: Instant
    ): ExternalIdentity {
        val otherGoogleIdentity = externalIdentityRepository.findByAccountIdAndProvider(
            account.id,
            ExternalAuthProvider.GOOGLE
        ).orElse(null)
        if (otherGoogleIdentity != null && otherGoogleIdentity.providerSubject != providerSubject) {
            logger.warn(
                "google.sso.link-identity rejected reason=already-linked-different-google accountId={} " +
                    "existingSubjectHash={} newSubjectHash={}",
                account.id,
                shortHash(otherGoogleIdentity.providerSubject),
                shortHash(providerSubject)
            )
            throw IdentityConflictException("This account is already linked to another Google account")
        }
        if (account.status == AccountStatus.SUSPENDED) {
            logger.warn("google.sso.link-identity rejected reason=account-suspended accountId={}", account.id)
            throw InvalidExternalIdentityException()
        }
        if (account.email == providerEmail && account.emailVerifiedAt == null) {
            account.verifyEmail(now)
        }
        if (otherGoogleIdentity == null) {
            logger.info(
                "google.sso.link-identity create accountId={} subjectHash={} emailHash={}",
                account.id,
                shortHash(providerSubject),
                shortHash(providerEmail)
            )
        }
        return otherGoogleIdentity ?: externalIdentityRepository.save(
            ExternalIdentity(
                accountId = account.id,
                provider = ExternalAuthProvider.GOOGLE,
                providerSubject = providerSubject,
                providerEmail = providerEmail,
                lastLoginAt = now
            )
        )
    }

    private fun authenticated(
        account: Account,
        identity: ExternalIdentity,
        providerEmail: String,
        device: DeviceMetadata,
        now: Instant
    ): GoogleAuthenticationResponse {
        if (account.status == AccountStatus.SUSPENDED) {
            logger.warn("google.sso.authenticated rejected reason=account-suspended accountId={}", account.id)
            throw InvalidExternalIdentityException()
        }
        identity.recordLogin(providerEmail, now)
        if (account.email == providerEmail && account.emailVerifiedAt == null) {
            account.verifyEmail(now)
        }
        val session = identitySessionService.create(account.id, device)
        logger.info(
            "google.sso.authenticated session-created accountId={} identityId={} platform={} emailHash={}",
            account.id,
            identity.id,
            device.platform,
            shortHash(providerEmail)
        )
        return GoogleAuthenticationResponse(
            status = GoogleAuthenticationStatus.AUTHENTICATED,
            account = accountResponseAssembler.assemble(account),
            session = session
        )
    }

    private fun GoogleAuthenticationResponse.toAuthResponse(): AuthResponse {
        val authenticatedAccount = account ?: throw InvalidExternalIdentityException()
        val authenticatedSession = session ?: throw InvalidExternalIdentityException()
        return AuthResponse(authenticatedAccount, authenticatedSession, nextVerification)
    }

    private fun generateRegistrationToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun shortHash(value: String): String = hash(value).take(12)

    private fun normalizeHandle(handle: String): String = handle.trim().lowercase(Locale.ROOT)

    private companion object {
        private val logger = LoggerFactory.getLogger(GoogleAuthenticationServiceImpl::class.java)
    }
}
