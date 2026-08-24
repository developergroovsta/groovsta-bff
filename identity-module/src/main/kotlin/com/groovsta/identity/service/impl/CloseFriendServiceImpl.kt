package com.groovsta.identity.service.impl

import com.groovsta.identity.dto.response.CloseFriendsResponse
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.CloseFriend
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.CloseFriendRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.CloseFriendService
import com.groovsta.identity.service.ProfileService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

@Service
class CloseFriendServiceImpl(
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val closeFriendRepository: CloseFriendRepository,
    private val profileService: ProfileService
) : CloseFriendService {

    @Transactional(readOnly = true)
    override fun list(ownerAccountId: UUID): CloseFriendsResponse {
        requireActive(ownerAccountId)
        val accountIds = closeFriendRepository.findByOwnerAccountIdOrderByCreatedAtDesc(ownerAccountId)
            .map(CloseFriend::friendAccountId)
        val profiles = profileService.mentionableSummaries(accountIds.toSet())
        return CloseFriendsResponse(accountIds.mapNotNull(profiles::get))
    }

    @Transactional
    override fun add(ownerAccountId: UUID, friendHandle: String): ProfileSummaryResponse {
        requireActive(ownerAccountId)
        val friend = profileRepository.findByHandle(normalizeHandle(friendHandle)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        if (friend.accountId == ownerAccountId) {
            throw IdentityConflictException("An account cannot add itself to close friends")
        }
        requireActive(friend.accountId)
        if (!closeFriendRepository.existsByOwnerAccountIdAndFriendAccountId(ownerAccountId, friend.accountId)) {
            closeFriendRepository.save(
                CloseFriend(ownerAccountId = ownerAccountId, friendAccountId = friend.accountId)
            )
        }
        return profileService.mentionableSummaries(setOf(friend.accountId))[friend.accountId]
            ?: throw IdentityNotFoundException("Profile was not found")
    }

    @Transactional
    override fun remove(ownerAccountId: UUID, friendHandle: String) {
        requireActive(ownerAccountId)
        val friend = profileRepository.findByHandle(normalizeHandle(friendHandle)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        closeFriendRepository.deleteByOwnerAccountIdAndFriendAccountId(ownerAccountId, friend.accountId)
    }

    private fun requireActive(accountId: UUID) {
        val account = accountRepository.findById(accountId).orElseThrow {
            IdentityNotFoundException("Account was not found")
        }
        if (account.status != AccountStatus.ACTIVE) {
            throw IdentityConflictException("Account must be active to manage close friends")
        }
    }

    private fun normalizeHandle(handle: String) = handle.trim().lowercase(Locale.ROOT)
}
