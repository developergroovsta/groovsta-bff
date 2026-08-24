package com.groovsta.identity.service.impl

import com.groovsta.identity.dto.request.CompleteAvatarUploadRequest
import com.groovsta.identity.dto.request.CreateAvatarUploadRequest
import com.groovsta.identity.dto.request.UpdateProfileRequest
import com.groovsta.identity.dto.response.ProfileResponse
import com.groovsta.identity.dto.response.ProfileRelationship
import com.groovsta.identity.dto.response.ProfileSearchResponse
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.identity.dto.response.ProfileViewerStateResponse
import com.groovsta.identity.dto.response.UploadUrlResponse
import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.FollowStatus
import com.groovsta.identity.model.Profile
import com.groovsta.identity.repository.FollowRepository
import com.groovsta.identity.repository.ContentMuteRepository
import com.groovsta.identity.model.ContentMuteType
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.ProfileService
import com.groovsta.shared.storage.ObjectStorageService
import com.groovsta.shared.storage.S3StorageProperties
import com.groovsta.shared.profile.ProfileContentStatsProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Base64
import java.util.Locale
import java.util.UUID

@Service
class ProfileServiceImpl(
    private val profileRepository: ProfileRepository,
    private val followRepository: FollowRepository,
    private val contentMuteRepository: ContentMuteRepository,
    private val objectStorageService: ObjectStorageService,
    private val storageProperties: S3StorageProperties,
    private val profileContentStatsProvider: ObjectProvider<ProfileContentStatsProvider>
) : ProfileService {

    @Transactional(readOnly = true)
    override fun getByAccountId(accountId: UUID): ProfileResponse {
        val profile = profileRepository.findByAccountId(accountId).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        return toResponse(profile, selfViewerState())
    }

    @Transactional(readOnly = true)
    override fun getByHandle(handle: String, viewerAccountId: UUID?): ProfileResponse {
        val profile = profileRepository.findByHandle(normalizeHandle(handle)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        return toResponse(profile, viewerStates(viewerAccountId, listOf(profile))[profile.accountId])
    }

    @Transactional(readOnly = true)
    override fun summaries(accountIds: Set<UUID>): Map<UUID, ProfileSummaryResponse> =
        if (accountIds.isEmpty()) emptyMap()
        else profileRepository.findByAccountIdIn(accountIds).associate { it.accountId to toSummary(it) }

    @Transactional(readOnly = true)
    override fun mentionableSummaries(accountIds: Set<UUID>): Map<UUID, ProfileSummaryResponse> =
        if (accountIds.isEmpty()) emptyMap()
        else profileRepository.findMentionableByAccountIdIn(accountIds, AccountStatus.ACTIVE)
            .associate { it.accountId to toSummary(it) }

    @Transactional(readOnly = true)
    override fun searchByHandleText(
        query: String,
        cursor: String?,
        limit: Int,
        viewerAccountId: UUID?
    ): ProfileSearchResponse {
        val normalizedText = query.trim().removePrefix("@").lowercase(Locale.ROOT)
        require(normalizedText.isNotEmpty()) { "Profile handle search text must not be blank" }
        require(normalizedText.length <= 30) { "Profile handle search text must not exceed 30 characters" }
        require(normalizedText.matches(HANDLE_SEARCH_PATTERN)) {
            "Profile handle search text may contain only letters, numbers, and underscores"
        }
        val pageSize = limit.coerceIn(1, 50)
        val afterHandle = cursor?.let(::decodeSearchCursor).orEmpty()
        val profiles = profileRepository.searchByHandleText(
            handlePattern = "%${escapeLikeLiteral(normalizedText)}%",
            afterHandle = afterHandle,
            status = AccountStatus.ACTIVE,
            pageable = PageRequest.of(0, pageSize + 1)
        )
        val page = profiles.take(pageSize)
        val viewerStates = viewerStates(viewerAccountId, page)
        return ProfileSearchResponse(
            items = page.map { profile -> toSummary(profile, viewerStates[profile.accountId]) },
            nextCursor = page.lastOrNull()?.takeIf { profiles.size > pageSize }?.handle?.let(::encodeSearchCursor)
        )
    }

    @Transactional
    override fun update(accountId: UUID, request: UpdateProfileRequest): ProfileResponse {
        val profile = profileRepository.findByAccountId(accountId).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        profile.update(
            displayName = request.displayName.trim(),
            bio = request.bio?.trim()?.takeIf { it.isNotEmpty() },
            isPublic = request.isPublic
        )
        return toResponse(profile, selfViewerState())
    }

    override fun createAvatarUpload(accountId: UUID, request: CreateAvatarUploadRequest): UploadUrlResponse {
        val extension = request.fileName.substringAfterLast('.', "bin").lowercase().take(8)
        val key = "$avatarPrefix/$accountId/${UUID.randomUUID()}.$extension"
        val upload = objectStorageService.createUpload(key, request.contentType, storageProperties.uploadUrlTtl)
        return UploadUrlResponse(upload.objectKey(), upload.uploadUrl(), upload.expiresAt())
    }

    @Transactional
    override fun completeAvatarUpload(accountId: UUID, request: CompleteAvatarUploadRequest): ProfileResponse {
        val expectedPrefix = "$avatarPrefix/$accountId/"
        require(request.objectKey.startsWith(expectedPrefix)) { "Avatar object key does not belong to this account" }
        require(objectStorageService.exists(request.objectKey)) { "Uploaded avatar was not found in S3" }
        val profile = profileRepository.findByAccountId(accountId).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }
        val oldKey = profile.avatarObjectKey
        profile.avatarObjectKey = request.objectKey
        if (oldKey != null && oldKey != request.objectKey) objectStorageService.delete(oldKey)
        return toResponse(profile, selfViewerState())
    }

    private fun toResponse(profile: Profile, viewerState: ProfileViewerStateResponse?) = ProfileResponse(
        accountId = profile.accountId,
        handle = profile.handle,
        displayName = profile.displayName,
        bio = profile.bio,
        avatarUrl = profile.avatarObjectKey?.let {
            objectStorageService.createDownloadUrl(it, storageProperties.downloadUrlTtl)
        },
        isPublic = profile.isPublic,
        followerCount = followRepository.countActiveFollowers(
            profile.accountId,
            AccountStatus.ACTIVE,
            FollowStatus.FOLLOWING
        ),
        followingCount = followRepository.countActiveFollowing(
            profile.accountId,
            AccountStatus.ACTIVE,
            FollowStatus.FOLLOWING
        ),
        postCount = profileContentStatsProvider.getIfAvailable()?.statsFor(profile.accountId)?.postCount() ?: 0,
        createdAt = profile.createdAt,
        viewerState = viewerState
    )

    private fun toSummary(
        profile: Profile,
        viewerState: ProfileViewerStateResponse? = null
    ) = ProfileSummaryResponse(
        accountId = profile.accountId,
        handle = profile.handle,
        displayName = profile.displayName,
        avatarUrl = profile.avatarObjectKey?.let {
            objectStorageService.createDownloadUrl(it, storageProperties.downloadUrlTtl)
        },
        viewerState = viewerState
    )

    private fun viewerStates(
        viewerAccountId: UUID?,
        profiles: Collection<Profile>
    ): Map<UUID, ProfileViewerStateResponse> {
        if (viewerAccountId == null || profiles.isEmpty()) return emptyMap()

        val targetIds = profiles.asSequence()
            .map(Profile::accountId)
            .filterNot(viewerAccountId::equals)
            .toSet()
        val edges = if (targetIds.isEmpty()) emptyList() else followRepository.findActiveRelationshipEdges(
            viewerAccountId = viewerAccountId,
            targetAccountIds = targetIds,
            status = AccountStatus.ACTIVE
        )
        val followedByViewer = edges.asSequence()
            .filter { it.followerAccountId == viewerAccountId && it.followStatus == FollowStatus.FOLLOWING }
            .mapTo(mutableSetOf()) { it.followedAccountId }
        val requestedByViewer = edges.asSequence()
            .filter { it.followerAccountId == viewerAccountId && it.followStatus == FollowStatus.REQUESTED }
            .mapTo(mutableSetOf()) { it.followedAccountId }
        val followsViewer = edges.asSequence()
            .filter { it.followedAccountId == viewerAccountId && it.followStatus == FollowStatus.FOLLOWING }
            .mapTo(mutableSetOf()) { it.followerAccountId }
        val muteTypesByTarget = if (targetIds.isEmpty()) emptyMap() else
            contentMuteRepository.findMuteEdges(viewerAccountId, targetIds)
                .groupBy({ it.mutedAccountId }, { it.contentType })

        return profiles.associate { profile ->
            val state = if (profile.accountId == viewerAccountId) {
                selfViewerState()
            } else {
                val relationship = when (profile.accountId) {
                    in followedByViewer -> ProfileRelationship.FOLLOWING
                    in requestedByViewer -> ProfileRelationship.REQUESTED
                    else -> ProfileRelationship.NONE
                }
                ProfileViewerStateResponse(
                    relationship = relationship,
                    followsViewer = profile.accountId in followsViewer,
                    canViewContent = profile.isPublic || relationship == ProfileRelationship.FOLLOWING,
                    postsMuted = ContentMuteType.POST in muteTypesByTarget[profile.accountId].orEmpty(),
                    reelsMuted = ContentMuteType.REEL in muteTypesByTarget[profile.accountId].orEmpty(),
                    storiesMuted = ContentMuteType.STORY in muteTypesByTarget[profile.accountId].orEmpty()
                )
            }
            profile.accountId to state
        }
    }

    private fun selfViewerState() = ProfileViewerStateResponse(
        relationship = ProfileRelationship.SELF,
        followsViewer = false,
        canViewContent = true,
        postsMuted = false,
        reelsMuted = false,
        storiesMuted = false
    )

    private fun encodeSearchCursor(handle: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(handle.toByteArray(Charsets.UTF_8))

    private fun decodeSearchCursor(cursor: String): String = runCatching {
        String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
    }.getOrElse { throw IllegalArgumentException("Invalid profile search cursor") }
        .also { require(it.matches(Regex("[a-z0-9_]{3,30}"))) { "Invalid profile search cursor" } }

    private fun normalizeHandle(handle: String) = handle.trim().lowercase(Locale.ROOT)

    private fun escapeLikeLiteral(value: String): String = value
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_")

    private val avatarPrefix: String
        get() = "${storageProperties.imagePrefix.trim('/')}/avatars"

    private companion object {
        val HANDLE_SEARCH_PATTERN = Regex("[a-z0-9_]+")
    }
}
