package com.groovsta.content.service.impl

import com.groovsta.content.config.ContentProperties
import com.groovsta.content.dto.CompleteReelUploadRequest
import com.groovsta.content.dto.CreateReelUploadRequest
import com.groovsta.content.dto.ReelResponse
import com.groovsta.content.dto.ReelUploadResponse
import com.groovsta.content.model.ContentItemTag
import com.groovsta.content.model.Reel
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.TranscodeJob
import com.groovsta.content.repository.ContentCommentRepository
import com.groovsta.content.repository.ContentItemTagRepository
import com.groovsta.content.repository.ContentLikeRepository
import com.groovsta.content.repository.ReelRepository
import com.groovsta.content.repository.SavedContentRepository
import com.groovsta.content.repository.TranscodeJobRepository
import com.groovsta.content.service.PublishedReelQueryService
import com.groovsta.content.service.ReelService
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.ProfileService
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import com.groovsta.shared.storage.ObjectStorageService
import com.groovsta.shared.storage.S3StorageProperties
import com.groovsta.shared.reward.RewardEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class ReelServiceImpl(
    private val reelRepository: ReelRepository,
    private val transcodeJobRepository: TranscodeJobRepository,
    private val likeRepository: ContentLikeRepository,
    private val commentRepository: ContentCommentRepository,
    private val tagRepository: ContentItemTagRepository,
    private val savedContentRepository: SavedContentRepository,
    private val profileRepository: ProfileRepository,
    private val profileService: ProfileService,
    private val notificationPublisher: NotificationPublisher,
    private val storage: ObjectStorageService,
    private val storageProperties: S3StorageProperties,
    private val properties: ContentProperties,
    private val rewardEventPublisher: RewardEventPublisher
) : ReelService, PublishedReelQueryService {

    @Transactional
    override fun createUpload(accountId: UUID, request: CreateReelUploadRequest): ReelUploadResponse {
        require(request.sizeBytes <= properties.maxReelBytes) { "Reel exceeds the configured upload limit" }
        val taggedProfiles = validateTaggedAccounts(request.taggedAccountIds)
        val reelId = UUID.randomUUID()
        val extension = request.fileName.substringAfterLast('.', "bin").lowercase().take(8)
        val key = "$reelPrefix/$reelId/source/${UUID.randomUUID()}.$extension"
        reelRepository.save(Reel(
            id = reelId,
            ownerAccountId = accountId,
            caption = request.caption?.trim()?.takeIf(String::isNotEmpty),
            sourceObjectKey = key,
            contentType = request.contentType,
            sourceSizeBytes = request.sizeBytes
        ))
        saveTags(reelId, request.taggedAccountIds, taggedProfiles)
        val upload = storage.createUpload(key, request.contentType, storageProperties.uploadUrlTtl)
        return ReelUploadResponse(reelId, key, upload.uploadUrl(), upload.expiresAt())
    }

    @Transactional
    override fun completeUpload(accountId: UUID, reelId: UUID, request: CompleteReelUploadRequest): ReelResponse {
        val reel = owned(reelId, accountId)
        require(reel.status == ContentStatus.UPLOADING) { "Reel upload is not awaiting completion" }
        require(request.objectKey == reel.sourceObjectKey) { "Object key does not match this reel" }
        require(storage.exists(request.objectKey)) { "Uploaded reel was not found in S3" }
        reel.status = ContentStatus.TRANSCODING
        reel.updatedAt = Instant.now()
        transcodeJobRepository.save(TranscodeJob(
            reelId = reel.id,
            sourceObjectKey = reel.sourceObjectKey,
            outputPrefix = "$reelPrefix/${reel.id}/published/"
        ))
        return response(reel, saved = false, liked = false)
    }

    @Transactional(readOnly = true)
    override fun get(accountId: UUID?, reelId: UUID): ReelResponse {
        val reel = find(reelId)
        if (reel.status != ContentStatus.PUBLISHED && reel.ownerAccountId != accountId) notFound()
        return response(
            reel,
            saved = savedState(accountId, reel.id),
            liked = likedState(accountId, reel.id),
            taggedAccounts = taggedAccounts(reel.id),
            canShareToStory = canShareToStory(accountId, reel.ownerAccountId)
        )
    }

    @Transactional(readOnly = true)
    override fun getAll(accountId: UUID?, reelIds: Collection<UUID>): Map<UUID, ReelResponse> {
        if (reelIds.isEmpty()) return emptyMap()
        val reels = reelRepository.findAllById(reelIds)
        val savedIds = savedContentIds(accountId, reelIds)
        val likedIds = likedContentIds(accountId, reelIds)
        val publicOwnerIds = publicOwnerIds(reels.map(Reel::ownerAccountId).toSet())
        val taggedAccounts = taggedAccountsByContentId(reels.map(Reel::id))
        return reels.associate { reel ->
            reel.id to response(
                reel,
                saved = accountId?.let { reel.id in savedIds },
                liked = reel.id in likedIds,
                taggedAccounts = taggedAccounts[reel.id].orEmpty(),
                canShareToStory = canShareToStory(accountId, reel.ownerAccountId, publicOwnerIds)
            )
        }
    }

    @Transactional
    override fun publish(accountId: UUID, reelId: UUID): ReelResponse {
        val reel = owned(reelId, accountId)
        require(reel.status == ContentStatus.READY_TO_PUBLISH) { "Reel has not finished transcoding" }
        reel.status = ContentStatus.PUBLISHED
        val now = Instant.now()
        reel.publishedAt = now
        reel.updatedAt = now
        rewardEventPublisher.reelPublished(accountId, reel.id, now)
        publishTagNotifications(reel)
        return response(reel, saved = false, liked = false, taggedAccounts = taggedAccounts(reel.id), canShareToStory = true)
    }

    @Transactional(readOnly = true)
    override fun latest(viewerAccountId: UUID?, before: Instant, limit: Int): List<ReelResponse> {
        val reels = reelRepository.findPublicReels(
            ContentStatus.PUBLISHED, before, PageRequest.of(0, limit.coerceIn(1, 50))
        )
        return responses(reels, viewerAccountId)
    }

    @Transactional(readOnly = true)
    override fun latestExcludingOwners(
        viewerAccountId: UUID?,
        before: Instant,
        excludedOwnerIds: Set<UUID>,
        limit: Int
    ): List<ReelResponse> {
        val reels = reelRepository.findPublicReelsExcludingOwners(
            ContentStatus.PUBLISHED,
            excludedOwnerIds.ifEmpty { setOf(NO_ACCOUNT) },
            before,
            PageRequest.of(0, limit.coerceIn(1, 50))
        )
        return responses(reels, viewerAccountId)
    }

    @Transactional(readOnly = true)
    override fun latestByOwners(
        viewerAccountId: UUID?,
        ownerIds: Set<UUID>,
        before: Instant,
        limit: Int
    ): List<ReelResponse> {
        if (ownerIds.isEmpty()) return emptyList()
        val reels = reelRepository.findByStatusAndOwnerAccountIdInAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
            ContentStatus.PUBLISHED, ownerIds, before, PageRequest.of(0, limit.coerceIn(1, 50))
        )
        return responses(reels, viewerAccountId)
    }

    private fun owned(reelId: UUID, accountId: UUID): Reel = find(reelId).also {
        if (it.ownerAccountId != accountId) notFound()
    }

    private fun find(reelId: UUID): Reel = reelRepository.findById(reelId).orElseThrow { notFound() }
    private fun notFound(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Reel was not found")

    private val reelPrefix: String
        get() = "${storageProperties.videoPrefix.trim('/')}/reels"

    private companion object {
        val NO_ACCOUNT: UUID = UUID(0, 0)
    }

    private fun response(reel: Reel, saved: Boolean?, liked: Boolean) = ReelResponse(
        id = reel.id,
        ownerAccountId = reel.ownerAccountId,
        caption = reel.caption,
        status = reel.status,
        playbackUrl = reel.publishedObjectKey?.let { storage.createDownloadUrl(it, storageProperties.downloadUrlTtl) },
        thumbnailUrl = reel.thumbnailObjectKey?.let { storage.createDownloadUrl(it, storageProperties.downloadUrlTtl) },
        durationSeconds = reel.durationSeconds,
        taggedAccounts = taggedAccounts(reel.id),
        canShareToStory = canShareToStory(null, reel.ownerAccountId),
        likeCount = likeRepository.countByContentId(reel.id),
        liked = liked,
        commentCount = commentRepository.countByContentId(reel.id),
        publishedAt = reel.publishedAt,
        createdAt = reel.createdAt,
        moderationReason = reel.moderationReason,
        saved = saved
    )

    private fun response(
        reel: Reel,
        saved: Boolean?,
        liked: Boolean,
        taggedAccounts: List<ProfileSummaryResponse>,
        canShareToStory: Boolean
    ) = ReelResponse(
        id = reel.id,
        ownerAccountId = reel.ownerAccountId,
        caption = reel.caption,
        status = reel.status,
        playbackUrl = reel.publishedObjectKey?.let { storage.createDownloadUrl(it, storageProperties.downloadUrlTtl) },
        thumbnailUrl = reel.thumbnailObjectKey?.let { storage.createDownloadUrl(it, storageProperties.downloadUrlTtl) },
        durationSeconds = reel.durationSeconds,
        taggedAccounts = taggedAccounts,
        canShareToStory = canShareToStory,
        likeCount = likeRepository.countByContentId(reel.id),
        liked = liked,
        commentCount = commentRepository.countByContentId(reel.id),
        publishedAt = reel.publishedAt,
        createdAt = reel.createdAt,
        moderationReason = reel.moderationReason,
        saved = saved
    )

    private fun responses(reels: List<Reel>, viewerAccountId: UUID?): List<ReelResponse> {
        val savedIds = savedContentIds(viewerAccountId, reels.map(Reel::id))
        val likedIds = likedContentIds(viewerAccountId, reels.map(Reel::id))
        val taggedAccounts = taggedAccountsByContentId(reels.map(Reel::id))
        val publicOwnerIds = publicOwnerIds(reels.map(Reel::ownerAccountId).toSet())
        return reels.map { reel ->
            response(
                reel,
                saved = viewerAccountId?.let { reel.id in savedIds },
                liked = reel.id in likedIds,
                taggedAccounts = taggedAccounts[reel.id].orEmpty(),
                canShareToStory = canShareToStory(viewerAccountId, reel.ownerAccountId, publicOwnerIds)
            )
        }
    }

    private fun canShareToStory(viewerAccountId: UUID?, ownerAccountId: UUID): Boolean =
        viewerAccountId != null && (viewerAccountId == ownerAccountId ||
            profileRepository.findByAccountId(ownerAccountId).orElse(null)?.isPublic == true)

    private fun canShareToStory(
        viewerAccountId: UUID?,
        ownerAccountId: UUID,
        publicOwnerIds: Set<UUID>
    ): Boolean = viewerAccountId != null && (viewerAccountId == ownerAccountId || ownerAccountId in publicOwnerIds)

    private fun publicOwnerIds(ownerAccountIds: Set<UUID>): Set<UUID> =
        if (ownerAccountIds.isEmpty()) emptySet()
        else profileRepository.findByAccountIdIn(ownerAccountIds)
            .filter { it.isPublic }
            .mapTo(mutableSetOf()) { it.accountId }

    private fun validateTaggedAccounts(taggedAccountIds: List<UUID>): Map<UUID, ProfileSummaryResponse> {
        require(taggedAccountIds.distinct().size == taggedAccountIds.size) {
            "A tagged account may appear only once"
        }
        if (taggedAccountIds.isEmpty()) return emptyMap()
        val profiles = profileService.mentionableSummaries(taggedAccountIds.toSet())
        require(profiles.size == taggedAccountIds.size) {
            "Every tagged account must exist and be active"
        }
        return profiles
    }

    private fun saveTags(
        contentId: UUID,
        taggedAccountIds: List<UUID>,
        profiles: Map<UUID, ProfileSummaryResponse>
    ) {
        if (taggedAccountIds.isEmpty()) return
        tagRepository.saveAll(taggedAccountIds.mapIndexed { position, accountId ->
            ContentItemTag(
                contentId = contentId,
                taggedAccountId = accountId,
                handle = requireNotNull(profiles[accountId]).handle,
                position = position
            )
        })
    }

    private fun publishTagNotifications(reel: Reel) {
        tagRepository.findByContentIdOrderByPositionAsc(reel.id).forEach { tag ->
            notificationPublisher.publish(
                NotificationCommand(
                    tag.taggedAccountId,
                    reel.ownerAccountId,
                    NotificationType.CONTENT_TAGGED,
                    NotificationTargetType.CONTENT,
                    reel.id,
                    tag.id,
                    "Tagged you in a reel"
                )
            )
        }
    }

    private fun taggedAccounts(contentId: UUID): List<ProfileSummaryResponse> =
        taggedAccountsByContentId(listOf(contentId))[contentId].orEmpty()

    private fun taggedAccountsByContentId(contentIds: Collection<UUID>): Map<UUID, List<ProfileSummaryResponse>> {
        if (contentIds.isEmpty()) return emptyMap()
        val tags = tagRepository.findByContentIdInOrderByContentIdAscPositionAsc(contentIds)
        val profiles = profileService.summaries(tags.mapTo(mutableSetOf(), ContentItemTag::taggedAccountId))
        return tags.groupBy(ContentItemTag::contentId).mapValues { (_, contentTags) ->
            contentTags.mapNotNull { profiles[it.taggedAccountId] }
        }
    }

    private fun likedState(accountId: UUID?, contentId: UUID): Boolean =
        accountId != null && likeRepository.existsByContentIdAndAccountId(contentId, accountId)

    private fun likedContentIds(accountId: UUID?, contentIds: Collection<UUID>): Set<UUID> =
        if (accountId == null || contentIds.isEmpty()) emptySet()
        else likeRepository.findLikedContentIds(accountId, contentIds)

    private fun savedState(accountId: UUID?, contentId: UUID): Boolean? =
        accountId?.let { savedContentRepository.findByAccountIdAndContentId(it, contentId) != null }

    private fun savedContentIds(accountId: UUID?, contentIds: Collection<UUID>): Set<UUID> =
        if (accountId == null || contentIds.isEmpty()) emptySet()
        else savedContentRepository.findByAccountIdAndContentIdIn(accountId, contentIds)
            .mapTo(mutableSetOf()) { it.contentId }
}
