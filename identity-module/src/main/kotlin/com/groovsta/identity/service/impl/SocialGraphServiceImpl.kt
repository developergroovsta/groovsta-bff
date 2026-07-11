package com.groovsta.identity.service.impl

import com.groovsta.identity.dto.response.FollowResponse
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.Follow
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.FollowRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.SocialGraphService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

@Service
class SocialGraphServiceImpl(
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val followRepository: FollowRepository
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
        if (!followRepository.existsByFollowerAccountIdAndFollowedAccountId(followerAccountId, target.accountId)) {
            followRepository.save(Follow(followerAccountId = followerAccountId, followedAccountId = target.accountId))
        }
        return FollowResponse(accountId = target.accountId, following = true)
    }

    @Transactional
    override fun unfollow(followerAccountId: UUID, targetHandle: String): FollowResponse {
        val target = profileRepository.findByHandle(normalizeHandle(targetHandle)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        followRepository.deleteByFollowerAccountIdAndFollowedAccountId(followerAccountId, target.accountId)
        return FollowResponse(accountId = target.accountId, following = false)
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
}
