package com.groovsta.identity.service.impl

import com.groovsta.identity.config.IdentityProperties
import com.groovsta.identity.dto.request.ConfirmPasswordResetRequest
import com.groovsta.identity.dto.request.LoginRequest
import com.groovsta.identity.dto.request.RequestPasswordResetRequest
import com.groovsta.identity.dto.request.SendEmailVerificationRequest
import com.groovsta.identity.dto.request.SendPhoneVerificationRequest
import com.groovsta.identity.dto.request.SignupRequest
import com.groovsta.identity.dto.response.AccountResponse
import com.groovsta.identity.dto.response.AuthResponse
import com.groovsta.identity.dto.response.DeviceMetadata
import com.groovsta.identity.dto.response.SessionResponse
import com.groovsta.identity.dto.response.VerificationChallengeResponse
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.exception.InvalidCredentialsException
import com.groovsta.identity.exception.InvalidVerificationException
import com.groovsta.identity.model.Account
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.Device
import com.groovsta.identity.model.Session
import com.groovsta.identity.model.VerificationChallenge
import com.groovsta.identity.model.VerificationChannel
import com.groovsta.identity.model.VerificationPurpose
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.DeviceRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.repository.SessionRepository
import com.groovsta.identity.repository.VerificationChallengeRepository
import com.groovsta.identity.service.AuthenticationService
import com.groovsta.identity.service.ProfileService
import com.groovsta.identity.service.VerificationDeliveryService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

@Service
class AuthenticationServiceImpl(
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val deviceRepository: DeviceRepository,
    private val sessionRepository: SessionRepository,
    private val verificationChallengeRepository: VerificationChallengeRepository,
    private val profileService: ProfileService,
    private val passwordEncoder: PasswordEncoder,
    private val properties: IdentityProperties,
    private val verificationDeliveryService: VerificationDeliveryService
) : AuthenticationService {

    private val secureRandom = SecureRandom()

    @Transactional
    override fun signup(request: SignupRequest, device: DeviceMetadata): AuthResponse {
        if (!request.adultDeclaration) {
            throw IllegalArgumentException("An 18+ declaration is required")
        }
        val email = request.email?.takeIf { it.isNotBlank() }?.let(::normalizeEmail)
        val phoneNumber = request.phoneNumber?.takeIf { it.isNotBlank() }?.let(::normalizePhoneNumber)
        if (email == null && phoneNumber == null) {
            throw IllegalArgumentException("Either email or phone number is required")
        }
        val handle = normalizeHandle(request.handle)
        if (email != null && accountRepository.existsByEmail(email)) {
            throw IdentityConflictException("An account already exists for this email")
        }
        if (phoneNumber != null && accountRepository.existsByPhoneNumber(phoneNumber)) {
            throw IdentityConflictException("An account already exists for this phone number")
        }
        if (profileRepository.existsByHandle(handle)) {
            throw IdentityConflictException("This handle is already in use")
        }

        val account = accountRepository.save(
            Account(
                email = email,
                phoneNumber = phoneNumber,
                passwordHash = passwordEncoder.encode(request.password),
                adultDeclaredAt = Instant.now()
            )
        )
        profileRepository.save(
            com.groovsta.identity.model.Profile(
                accountId = account.id,
                handle = handle,
                displayName = request.displayName.trim()
            )
        )
        val deviceRecord = registerDevice(account.id, device)
        val createdSession = createSession(account.id, deviceRecord.id)
        val verification = when {
            email != null -> issueChallenge(
                account,
                VerificationChannel.EMAIL,
                VerificationPurpose.ACCOUNT_VERIFICATION,
                email
            )

            phoneNumber != null -> issueChallenge(
                account,
                VerificationChannel.PHONE,
                VerificationPurpose.ACCOUNT_VERIFICATION,
                phoneNumber
            )

            else -> null
        }

        return AuthResponse(
            account = accountResponse(account),
            session = createdSession.response,
            nextVerification = verification
        )
    }

    @Transactional
    override fun login(request: LoginRequest, device: DeviceMetadata): AuthResponse {
        val email = request.email?.takeIf { it.isNotBlank() }?.let(::normalizeEmail)
        val phoneNumber = request.phoneNumber?.takeIf { it.isNotBlank() }?.let(::normalizePhoneNumber)
        if (listOf(email, phoneNumber).count { it != null } != 1) {
            throw IllegalArgumentException("Provide either email or phone number, not both")
        }
        val account = when {
            email != null -> accountRepository.findByEmail(email)
            phoneNumber != null -> accountRepository.findByPhoneNumber(phoneNumber)
            else -> throw InvalidCredentialsException()
        }.orElseThrow {
            InvalidCredentialsException()
        }
        if (account.status == AccountStatus.SUSPENDED || !passwordEncoder.matches(
                request.password,
                account.passwordHash
            )
        ) {
            throw InvalidCredentialsException()
        }
        val deviceRecord = registerDevice(account.id, device)
        val createdSession = createSession(account.id, deviceRecord.id)
        return AuthResponse(accountResponse(account), createdSession.response, null)
    }

    @Transactional
    override fun authenticate(accessToken: String): UUID {
        val session = sessionRepository.findByTokenHash(hash(accessToken)).orElseThrow { InvalidCredentialsException() }
        if (!session.isUsableAt(Instant.now())) {
            throw InvalidCredentialsException()
        }
        val account = accountRepository.findById(session.accountId).orElseThrow { InvalidCredentialsException() }
        if (account.status == AccountStatus.SUSPENDED) {
            throw InvalidCredentialsException()
        }
        session.markSeen()
        return account.id
    }

    @Transactional
    override fun logout(accountId: UUID, accessToken: String) {
        val session = sessionRepository.findByTokenHash(hash(accessToken)).orElseThrow { InvalidCredentialsException() }
        if (session.accountId != accountId || !session.isUsableAt(Instant.now())) {
            throw InvalidCredentialsException()
        }
        session.revoke()
    }

    @Transactional
    override fun requestEmailVerification(
        accountId: UUID,
        request: SendEmailVerificationRequest?
    ): VerificationChallengeResponse {
        val account = getAccount(accountId)
        if (account.emailVerifiedAt != null) {
            throw IdentityConflictException("Email is already verified")
        }
        val requestedEmail = request?.email?.takeIf { it.isNotBlank() }?.let(::normalizeEmail)
        if (requestedEmail != null && requestedEmail != account.email) {
            if (accountRepository.existsByEmail(requestedEmail)) {
                throw IdentityConflictException("An account already exists for this email")
            }
            account.email = requestedEmail
            account.emailVerifiedAt = null
        }
        val email = account.email ?: throw IllegalArgumentException("Email is required to request email verification")
        return issueChallenge(account, VerificationChannel.EMAIL, VerificationPurpose.ACCOUNT_VERIFICATION, email)
    }

    @Transactional
    override fun confirmEmailVerification(accountId: UUID, code: String): AccountResponse {
        val account = getAccount(accountId)
        val email = account.email ?: throw IllegalArgumentException("Email is required to confirm email verification")
        verifyChallenge(account.id, VerificationPurpose.ACCOUNT_VERIFICATION, VerificationChannel.EMAIL, email, code)
        account.verifyEmail(Instant.now())
        return accountResponse(account)
    }

    @Transactional
    override fun requestPhoneVerification(
        accountId: UUID,
        request: SendPhoneVerificationRequest
    ): VerificationChallengeResponse {
        val account = getAccount(accountId)
        if (account.phoneVerifiedAt != null) {
            throw IdentityConflictException("Phone number is already verified")
        }
        val phoneNumber = normalizePhoneNumber(request.phoneNumber)
        if (phoneNumber != account.phoneNumber) {
            if (accountRepository.existsByPhoneNumber(phoneNumber)) {
                throw IdentityConflictException("An account already exists for this phone number")
            }
            account.phoneNumber = phoneNumber
            account.phoneVerifiedAt = null
        }
        return issueChallenge(account, VerificationChannel.PHONE, VerificationPurpose.ACCOUNT_VERIFICATION, phoneNumber)
    }

    @Transactional
    override fun confirmPhoneVerification(accountId: UUID, phoneNumber: String, code: String): AccountResponse {
        val account = getAccount(accountId)
        val normalizedPhoneNumber = normalizePhoneNumber(phoneNumber)
        if (account.phoneNumber != normalizedPhoneNumber) {
            throw InvalidVerificationException("No open verification challenge exists")
        }
        verifyChallenge(
            account.id,
            VerificationPurpose.ACCOUNT_VERIFICATION,
            VerificationChannel.PHONE,
            normalizedPhoneNumber,
            code
        )
        account.verifyPhone(normalizedPhoneNumber, Instant.now())
        return accountResponse(account)
    }

    @Transactional
    override fun requestPasswordReset(request: RequestPasswordResetRequest): VerificationChallengeResponse {
        val passwordResetTarget = passwordResetTarget(request.email, request.phoneNumber)
        val account =
            accountRepository.findById(passwordResetTarget.accountId).orElseThrow { InvalidCredentialsException() }

        return issueChallenge(
            account,
            passwordResetTarget.channel,
            VerificationPurpose.PASSWORD_RESET,
            passwordResetTarget.destination
        )
    }

    @Transactional
    override fun confirmPasswordReset(request: ConfirmPasswordResetRequest) {
        val passwordResetTarget = passwordResetTarget(request.email, request.phoneNumber)
        val account =
            accountRepository.findById(passwordResetTarget.accountId).orElseThrow { InvalidCredentialsException() }

        verifyChallenge(
            account.id,
            VerificationPurpose.PASSWORD_RESET,
            passwordResetTarget.channel,
            passwordResetTarget.destination,
            request.code
        )

        account.passwordHash = passwordEncoder.encode(request.newPassword)
        sessionRepository.findAllByAccountId(account.id).forEach { it.revoke() }
    }

    private fun getAccount(accountId: UUID): Account = accountRepository.findById(accountId).orElseThrow {
        IdentityNotFoundException("Account was not found")
    }

    private fun registerDevice(accountId: UUID, device: DeviceMetadata): Device {
        val identifier = device.deviceIdentifier.trim()
        require(identifier.isNotEmpty()) { "X-Device-Id is required" }
        return deviceRepository.findByAccountIdAndDeviceIdentifier(accountId, identifier)
            .orElse(null)
            ?.also { it.seen(device.platform, device.appVersion?.trim(), device.userAgent?.take(1024)) }
            ?: deviceRepository.save(
                Device(
                    accountId = accountId,
                    deviceIdentifier = identifier,
                    platform = device.platform,
                    appVersion = device.appVersion?.trim(),
                    lastUserAgent = device.userAgent?.take(1024)
                )
            )
    }

    private fun createSession(accountId: UUID, deviceId: UUID): CreatedSession {
        val rawToken = generateAccessToken()
        val session = sessionRepository.save(
            Session(
                accountId = accountId,
                deviceId = deviceId,
                tokenHash = hash(rawToken),
                expiresAt = Instant.now().plus(properties.sessionTtl)
            )
        )
        return CreatedSession(SessionResponse(rawToken, "Bearer", session.expiresAt))
    }

    private fun issueChallenge(
        account: Account,
        channel: VerificationChannel,
        purpose: VerificationPurpose,
        destination: String
    ): VerificationChallengeResponse {
        verificationChallengeRepository
            .findByAccountIdAndPurposeAndChannelAndDestinationAndVerifiedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                account.id,
                purpose,
                channel,
                destination
            )
            .forEach { it.invalidate() }

        val code = "%06d".format(Locale.ROOT, secureRandom.nextInt(1_000_000))
        val expiresAt = Instant.now().plus(
            if (channel == VerificationChannel.EMAIL) properties.emailVerificationTtl else properties.phoneVerificationTtl
        )
        val challenge = verificationChallengeRepository.save(
            VerificationChallenge(
                accountId = account.id,
                channel = channel,
                purpose = purpose,
                destination = destination,
                codeHash = hashVerificationCode(code),
                expiresAt = expiresAt
            )
        )
        verificationDeliveryService.send(channel, destination, code)
        return VerificationChallengeResponse(channel, mask(destination, channel), challenge.expiresAt)
    }

    private fun verifyChallenge(
        accountId: UUID,
        purpose: VerificationPurpose,
        channel: VerificationChannel,
        destination: String,
        code: String
    ) {
        val challenge = verificationChallengeRepository
            .findByAccountIdAndPurposeAndChannelAndDestinationAndVerifiedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                accountId,
                purpose,
                channel,
                destination
            )
            .firstOrNull()
            ?: throw InvalidVerificationException("No open verification challenge exists")

        if (!challenge.isOpenAt(Instant.now())) {
            throw InvalidVerificationException("The verification code has expired or was invalidated")
        }
        if (!constantTimeEquals(challenge.codeHash, hashVerificationCode(code))) {
            challenge.recordFailedAttempt(properties.maxVerificationAttempts)
            throw InvalidVerificationException("The verification code is invalid")
        }
        challenge.verify()
    }

    private fun accountResponse(account: Account) = AccountResponse(
        id = account.id,
        email = account.email,
        phoneNumber = account.phoneNumber,
        status = account.status,
        emailVerified = account.emailVerifiedAt != null,
        phoneVerified = account.phoneVerifiedAt != null,
        profile = profileService.getByAccountId(account.id)
    )

    private fun generateAccessToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hashVerificationCode(code: String): String {
        require(properties.verificationPepper.isNotBlank()) { "identity.auth.verification-pepper must be configured" }
        return hash("${properties.verificationPepper}:$code")
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.toByteArray(StandardCharsets.US_ASCII),
        right.toByteArray(StandardCharsets.US_ASCII)
    )

    private fun normalizeEmail(email: String) = email.trim().lowercase(Locale.ROOT)

    private fun normalizePhoneNumber(phoneNumber: String) = phoneNumber.trim()

    private fun normalizeHandle(handle: String) = handle.trim().lowercase(Locale.ROOT)

    private fun passwordResetTarget(email: String?, phoneNumber: String?): PasswordResetTarget {
        val normalizedEmail = email?.takeIf { it.isNotBlank() }?.let(::normalizeEmail)
        val normalizedPhoneNumber = phoneNumber?.takeIf { it.isNotBlank() }?.let(::normalizePhoneNumber)
        if (listOf(normalizedEmail, normalizedPhoneNumber).count { it != null } != 1) {
            throw IllegalArgumentException("Provide either email or phone number, not both")
        }

        if (normalizedEmail != null) {
            val account = accountRepository.findByEmail(normalizedEmail).orElseThrow { InvalidCredentialsException() }
            if (account.emailVerifiedAt == null) {
                throw InvalidVerificationException("Email must be verified before password reset")
            }
            return PasswordResetTarget(account.id, VerificationChannel.EMAIL, normalizedEmail)
        }

        val account = accountRepository.findByPhoneNumber(normalizedPhoneNumber!!).orElseThrow {
            InvalidCredentialsException()
        }
        if (account.phoneVerifiedAt == null) {
            throw InvalidVerificationException("Phone number must be verified before password reset")
        }
        return PasswordResetTarget(account.id, VerificationChannel.PHONE, normalizedPhoneNumber)
    }

    private fun mask(destination: String, channel: VerificationChannel): String = when (channel) {
        VerificationChannel.EMAIL -> {
            val at = destination.indexOf('@')
            if (at <= 1) "***${destination.substring(at.coerceAtLeast(0))}"
            else "${destination.first()}***${destination.substring(at)}"
        }

        VerificationChannel.PHONE -> "***${destination.takeLast(4)}"
    }

    private data class CreatedSession(val response: SessionResponse)

    private data class PasswordResetTarget(
        val accountId: UUID,
        val channel: VerificationChannel,
        val destination: String
    )
}
