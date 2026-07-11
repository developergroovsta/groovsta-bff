package com.groovsta.identity.service.impl

import com.groovsta.identity.dto.request.UpdateProfileRequest
import com.groovsta.identity.dto.response.ProfileResponse
import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.model.Profile
import com.groovsta.identity.repository.FollowRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.ProfileService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

@Service
class ProfileServiceImpl(
    private val profileRepository: ProfileRepository,
    private val followRepository: FollowRepository
) : ProfileService {

    @Transactional(readOnly = true)
    override fun getByAccountId(accountId: UUID): ProfileResponse =
        toResponse(profileRepository.findByAccountId(accountId).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        })

    @Transactional(readOnly = true)
    override fun getByHandle(handle: String): ProfileResponse =
        toResponse(profileRepository.findByHandle(normalizeHandle(handle)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        })

    @Transactional
    override fun update(accountId: UUID, request: UpdateProfileRequest): ProfileResponse {
        val profile = profileRepository.findByAccountId(accountId).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        profile.update(
            displayName = request.displayName.trim(),
            bio = request.bio?.trim()?.takeIf { it.isNotEmpty() },
            avatarUrl = request.avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
        )
        return toResponse(profile)
    }

    private fun toResponse(profile: Profile) = ProfileResponse(
        accountId = profile.accountId,
        handle = profile.handle,
        displayName = profile.displayName,
        bio = profile.bio,
        avatarUrl = profile.avatarUrl,
        followerCount = followRepository.countByFollowedAccountId(profile.accountId),
        followingCount = followRepository.countByFollowerAccountId(profile.accountId),
        createdAt = profile.createdAt
    )

    private fun normalizeHandle(handle: String) = handle.trim().lowercase(Locale.ROOT)
}
