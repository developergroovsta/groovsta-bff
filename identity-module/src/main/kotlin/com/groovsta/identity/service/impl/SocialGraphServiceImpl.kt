package com.groovsta.identity.service.impl

import com.groovsta.identity.dto.response.FollowResponse
import com.groovsta.identity.dto.response.FollowRequestDecisionResponse
import com.groovsta.identity.dto.response.ProfileRelationship
import com.groovsta.identity.dto.response.ProfileSummaryPageResponse
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.Follow
import com.groovsta.identity.model.FollowStatus
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.FollowRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.SocialGraphService
import com.groovsta.identity.service.ProfileService
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import com.groovsta.shared.reward.RewardEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.Locale
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class SocialGraphServiceImpl(
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val followRepository: FollowRepository,
    private val profileService: ProfileService,
    private val notificationPublisher: NotificationPublisher,
    private val rewardEventPublisher: RewardEventPublisher
) : SocialGraphService {

    @Transactional
    override fun follow(followerAccountId: UUID, targetHandle: String): FollowResponse {
        val target = profileRepository.findByHandle(normalizeHandle(targetHandle)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        if (target.accountId == followerAccountId) {
            throw IdentityConflictException("An account cannot follow itself")
        }
        requireActive(followerAccountId)
        requireActive(target.accountId)
        val now = Instant.now()
        val desiredStatus = if (target.isPublic) FollowStatus.FOLLOWING else FollowStatus.REQUESTED
        val existing = followRepository.findByFollowerAccountIdAndFollowedAccountId(followerAccountId, target.accountId)
        var becameFollowing = false
        val connection = existing?.also {
            if (it.status == FollowStatus.REQUESTED && desiredStatus == FollowStatus.FOLLOWING) {
                it.status = FollowStatus.FOLLOWING
                it.followedAt = now
                becameFollowing = true
                notificationPublisher.resolve(NotificationType.FOLLOW_REQUEST, it.id)
            }
        } ?: followRepository.save(
            Follow(
                followerAccountId = followerAccountId,
                followedAccountId = target.accountId,
                status = desiredStatus,
                followedAt = if (desiredStatus == FollowStatus.FOLLOWING) now else null,
                createdAt = now
            )
        ).also { becameFollowing = it.status == FollowStatus.FOLLOWING }
        if (becameFollowing) {
            rewardEventPublisher.followEstablished(target.accountId, followerAccountId, connection.id, connection.followedAt ?: now)
        }
        if (existing == null) {
            when (desiredStatus) {
                FollowStatus.REQUESTED -> notificationPublisher.publish(
                    NotificationCommand(
                        target.accountId,
                        followerAccountId,
                        NotificationType.FOLLOW_REQUEST,
                        NotificationTargetType.PROFILE,
                        followerAccountId,
                        connection.id,
                        null
                    )
                )

                FollowStatus.FOLLOWING -> notificationPublisher.publish(
                    NotificationCommand(
                        target.accountId,
                        followerAccountId,
                        NotificationType.NEW_FOLLOWER,
                        NotificationTargetType.PROFILE,
                        followerAccountId,
                        connection.id,
                        null
                    )
                )
            }
        }
        return FollowResponse(
            accountId = target.accountId,
            relationship = if (connection.status == FollowStatus.FOLLOWING) {
                ProfileRelationship.FOLLOWING
            } else {
                ProfileRelationship.REQUESTED
            },
            following = connection.status == FollowStatus.FOLLOWING,
            followerCount = followRepository.countActiveFollowers(
                target.accountId,
                AccountStatus.ACTIVE,
                FollowStatus.FOLLOWING
            )
        )
    }

    @Transactional
    override fun unfollow(followerAccountId: UUID, targetHandle: String): FollowResponse {
        val target = profileRepository.findByHandle(normalizeHandle(targetHandle)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        followRepository.findByFollowerAccountIdAndFollowedAccountId(followerAccountId, target.accountId)?.let {
            followRepository.delete(it)
            if (it.status == FollowStatus.REQUESTED) {
                notificationPublisher.resolve(NotificationType.FOLLOW_REQUEST, it.id)
            }
        }
        return FollowResponse(
            accountId = target.accountId,
            relationship = ProfileRelationship.NONE,
            following = false,
            followerCount = followRepository.countActiveFollowers(
                target.accountId,
                AccountStatus.ACTIVE,
                FollowStatus.FOLLOWING
            )
        )
    }

    @Transactional
    override fun acceptFollowRequest(accountId: UUID, requestId: UUID): FollowRequestDecisionResponse {
        requireActive(accountId)
        val request = followRepository.findByIdAndFollowedAccountIdAndStatus(
            requestId,
            accountId,
            FollowStatus.REQUESTED
        ) ?: throw IdentityNotFoundException("Follow request was not found")
        val now = Instant.now()
        request.status = FollowStatus.FOLLOWING
        request.followedAt = now
        notificationPublisher.resolve(NotificationType.FOLLOW_REQUEST, request.id)
        notificationPublisher.publish(
            NotificationCommand(
                request.followerAccountId,
                accountId,
                NotificationType.FOLLOW_REQUEST_ACCEPTED,
                NotificationTargetType.PROFILE,
                accountId,
                request.id,
                null
            )
        )
        rewardEventPublisher.followEstablished(accountId, request.followerAccountId, request.id, now)
        return FollowRequestDecisionResponse(request.id, request.followerAccountId, request.status)
    }

    @Transactional
    override fun rejectFollowRequest(accountId: UUID, requestId: UUID) {
        requireActive(accountId)
        val request = followRepository.findByIdAndFollowedAccountIdAndStatus(
            requestId,
            accountId,
            FollowStatus.REQUESTED
        ) ?: throw IdentityNotFoundException("Follow request was not found")
        followRepository.delete(request)
        notificationPublisher.resolve(NotificationType.FOLLOW_REQUEST, request.id)
    }

    @Transactional(readOnly = true)
    override fun followers(
        targetHandle: String,
        cursor: String?,
        limit: Int,
        viewerAccountId: UUID?
    ): ProfileSummaryPageResponse {
        val target = targetProfile(targetHandle)
        requireCanViewConnectionList(target.accountId, target.isPublic, viewerAccountId)
        return connections(cursor, limit) { decoded, pageable ->
            decoded?.let {
                followRepository.findFollowersAfter(
                    target.accountId,
                    AccountStatus.ACTIVE,
                    FollowStatus.FOLLOWING,
                    it.createdAt,
                    it.id,
                    pageable
                )
            } ?: followRepository.findFollowers(
                target.accountId,
                AccountStatus.ACTIVE,
                FollowStatus.FOLLOWING,
                pageable
            )
        }.let { page -> connectionPage(page) { it.followerAccountId } }
    }

    @Transactional(readOnly = true)
    override fun following(
        targetHandle: String,
        cursor: String?,
        limit: Int,
        viewerAccountId: UUID?
    ): ProfileSummaryPageResponse {
        val target = targetProfile(targetHandle)
        requireCanViewConnectionList(target.accountId, target.isPublic, viewerAccountId)
        return connections(cursor, limit) { decoded, pageable ->
            decoded?.let {
                followRepository.findFollowingAfter(
                    target.accountId,
                    AccountStatus.ACTIVE,
                    FollowStatus.FOLLOWING,
                    it.createdAt,
                    it.id,
                    pageable
                )
            } ?: followRepository.findFollowing(
                target.accountId,
                AccountStatus.ACTIVE,
                FollowStatus.FOLLOWING,
                pageable
            )
        }.let { page -> connectionPage(page) { it.followedAccountId } }
    }

    @Transactional(readOnly = true)
    override fun followingAccountIds(followerAccountId: UUID): Set<UUID> =
        followRepository.findActiveFollowingAccountIds(
            followerAccountId,
            AccountStatus.ACTIVE,
            FollowStatus.FOLLOWING
        ).toSet()

    @Transactional(readOnly = true)
    override fun isFollowing(followerAccountId: UUID, followedAccountId: UUID): Boolean =
        followRepository.existsActiveFollow(
            followerAccountId,
            followedAccountId,
            AccountStatus.ACTIVE,
            FollowStatus.FOLLOWING
        )

    private fun connections(
        cursor: String?,
        limit: Int,
        query: (ConnectionCursor?, PageRequest) -> List<Follow>
    ): ConnectionResult {
        val pageSize = limit.coerceIn(1, 50)
        return ConnectionResult(query(cursor?.let(ConnectionCursor::decode), PageRequest.of(0, pageSize + 1)), pageSize)
    }

    private fun connectionPage(result: ConnectionResult, accountId: (Follow) -> UUID): ProfileSummaryPageResponse {
        val page = result.connections.take(result.pageSize)
        val accountIds = page.map(accountId)
        val profiles = profileService.summaries(accountIds.toSet())
        return ProfileSummaryPageResponse(
            items = accountIds.map { id ->
                profiles[id] ?: throw IdentityNotFoundException("Profile was not found")
            },
            nextCursor = page.lastOrNull()
                ?.takeIf { result.connections.size > result.pageSize }
                ?.let(ConnectionCursor::from)
                ?.encode()
        )
    }

    private fun targetProfile(handle: String) =
        profileRepository.findByHandle(normalizeHandle(handle)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }

    private fun requireCanViewConnectionList(
        targetAccountId: UUID,
        targetIsPublic: Boolean,
        viewerAccountId: UUID?
    ) {
        if (targetIsPublic || viewerAccountId == targetAccountId) return
        val viewerCanView = viewerAccountId != null && followRepository.existsActiveFollow(
            viewerAccountId,
            targetAccountId,
            AccountStatus.ACTIVE,
            FollowStatus.FOLLOWING
        )
        if (!viewerCanView) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Follower and following lists are private for this profile"
            )
        }
    }

    private fun requireActive(accountId: UUID) {
        val account = accountRepository.findById(accountId).orElseThrow {
            IdentityNotFoundException("Account was not found")
        }
        if (account.status != AccountStatus.ACTIVE) {
            throw IdentityConflictException("Account must complete email and phone verification before following users")
        }
    }

    private fun normalizeHandle(handle: String) = handle.trim().lowercase(Locale.ROOT)

    private data class ConnectionResult(val connections: List<Follow>, val pageSize: Int)

    private data class ConnectionCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$createdAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(connection: Follow) = ConnectionCursor(connection.createdAt, connection.id)

            fun decode(value: String): ConnectionCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid social graph cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid social graph cursor" }
                return ConnectionCursor(
                    createdAt = runCatching { Instant.parse(parts[0]) }
                        .getOrElse { throw IllegalArgumentException("Invalid social graph cursor") },
                    id = runCatching { UUID.fromString(parts[1]) }
                        .getOrElse { throw IllegalArgumentException("Invalid social graph cursor") }
                )
            }
        }
    }
}
