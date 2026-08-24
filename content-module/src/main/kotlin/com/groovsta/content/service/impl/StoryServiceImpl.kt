package com.groovsta.content.service.impl

import com.groovsta.content.config.ContentProperties
import com.groovsta.content.dto.CompleteStoryUploadRequest
import com.groovsta.content.dto.CreateStoryMediaRequest
import com.groovsta.content.dto.CreateStoryRequest
import com.groovsta.content.dto.StoryCreateResponse
import com.groovsta.content.dto.StoryHighlightPageResponse
import com.groovsta.content.dto.StoryHighlightResponse
import com.groovsta.content.dto.StoryHighlightSummaryResponse
import com.groovsta.content.dto.StoryLinkedContentResponse
import com.groovsta.content.dto.StoryLinkedStoryResponse
import com.groovsta.content.dto.StoryPageResponse
import com.groovsta.content.dto.StoryResponse
import com.groovsta.content.dto.StorySeenResponse
import com.groovsta.content.dto.StoryUploadResponse
import com.groovsta.content.dto.StoryViewerStateResponse
import com.groovsta.content.dto.UpsertStoryHighlightRequest
import com.groovsta.content.model.ContentItem
import com.groovsta.content.model.ContentKind
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.Post
import com.groovsta.content.model.PostMedia
import com.groovsta.content.model.Reel
import com.groovsta.content.model.Story
import com.groovsta.content.model.StoryHighlight
import com.groovsta.content.model.StoryHighlightItem
import com.groovsta.content.model.StoryMediaType
import com.groovsta.content.model.StoryMention
import com.groovsta.content.model.StoryStatus
import com.groovsta.content.model.StoryVisibility
import com.groovsta.content.model.StoryView
import com.groovsta.content.model.TranscodeJob
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.repository.ContentCommentRepository
import com.groovsta.content.repository.ContentLikeRepository
import com.groovsta.content.repository.PostMediaRepository
import com.groovsta.content.repository.StoryMentionRepository
import com.groovsta.content.repository.StoryHighlightItemRepository
import com.groovsta.content.repository.StoryHighlightRepository
import com.groovsta.content.repository.StoryRepository
import com.groovsta.content.repository.StoryViewRepository
import com.groovsta.content.repository.TranscodeJobRepository
import com.groovsta.content.service.StoryService
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.identity.dto.response.ProfileSummaryPageResponse
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.ContentMuteType
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.CloseFriendRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.ProfileService
import com.groovsta.identity.service.ContentMuteService
import com.groovsta.identity.service.SocialGraphService
import com.groovsta.shared.storage.ObjectStorageService
import com.groovsta.shared.storage.S3StorageProperties
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID

@Service
class StoryServiceImpl(
    private val storyRepository: StoryRepository,
    private val storyHighlightRepository: StoryHighlightRepository,
    private val storyHighlightItemRepository: StoryHighlightItemRepository,
    private val storyMentionRepository: StoryMentionRepository,
    private val storyViewRepository: StoryViewRepository,
    private val contentLikeRepository: ContentLikeRepository,
    private val contentCommentRepository: ContentCommentRepository,
    private val contentRepository: ContentItemRepository,
    private val postMediaRepository: PostMediaRepository,
    private val transcodeJobRepository: TranscodeJobRepository,
    private val accountRepository: AccountRepository,
    private val closeFriendRepository: CloseFriendRepository,
    private val profileRepository: ProfileRepository,
    private val profileService: ProfileService,
    private val contentMuteService: ContentMuteService,
    private val socialGraphService: SocialGraphService,
    private val storage: ObjectStorageService,
    private val storageProperties: S3StorageProperties,
    private val properties: ContentProperties
) : StoryService {

    @Transactional
    override fun create(accountId: UUID, request: CreateStoryRequest): StoryCreateResponse {
        requireActive(accountId)
        require(request.media != null || request.linkedContentId != null || request.linkedStoryId != null) {
            "A story requires uploaded media or a linked post, reel, or story"
        }
        require(request.linkedStoryId == null || (request.media == null && request.linkedContentId == null)) {
            "A linked story cannot be combined with uploaded media or linked content"
        }
        require(request.taggedAccountIds.distinct().size == request.taggedAccountIds.size) {
            "A tagged account may appear only once"
        }

        val linkedContent = request.linkedContentId?.let { shareablePublishedContent(accountId, it) }
        val linkedStory = request.linkedStoryId?.let { shareableStorySource(accountId, it) }
        val storyId = UUID.randomUUID()
        val story = when {
            request.media != null -> uploadedStory(storyId, accountId, request, request.media)
            linkedContent != null -> linkedContentStory(storyId, accountId, request, linkedContent)
            else -> resharedStory(storyId, accountId, request, requireNotNull(linkedStory))
        }

        storyRepository.save(story)
        val taggedProfiles = profileService.mentionableSummaries(request.taggedAccountIds.toSet())
        require(taggedProfiles.size == request.taggedAccountIds.size) {
            "Every tagged account must exist and be active"
        }
        storyMentionRepository.saveAll(request.taggedAccountIds.map { taggedAccountId ->
            StoryMention(
                storyId = story.id,
                mentionedAccountId = taggedAccountId,
                handle = requireNotNull(taggedProfiles[taggedAccountId]).handle
            )
        })

        val upload = request.media?.let {
            val presigned = storage.createUpload(story.sourceObjectKey, story.contentType, storageProperties.uploadUrlTtl)
            StoryUploadResponse(presigned.objectKey(), presigned.uploadUrl(), presigned.expiresAt())
        }
        return StoryCreateResponse(
            story = response(story, accountId, taggedProfiles.values.toList(), linkedContent),
            upload = upload
        )
    }

    @Transactional
    override fun completeUpload(
        accountId: UUID,
        storyId: UUID,
        request: CompleteStoryUploadRequest
    ): StoryResponse {
        val story = owned(storyId, accountId)
        require(story.ownsMedia) { "A shared post, reel, or story does not have a separate story upload" }
        require(story.status == StoryStatus.UPLOADING) { "Story upload is not awaiting completion" }
        require(request.objectKey == story.sourceObjectKey) { "Object key does not match this story" }
        require(storage.exists(request.objectKey)) { "Uploaded story media was not found in S3" }

        val now = Instant.now()
        story.publishedAt = now
        story.expiresAt = now.plus(STORY_LIFETIME)
        story.updatedAt = now
        if (story.mediaType == StoryMediaType.IMAGE) {
            story.publishedObjectKey = story.sourceObjectKey
            story.status = StoryStatus.PUBLISHED
        } else {
            story.status = StoryStatus.TRANSCODING
            transcodeJobRepository.save(
                TranscodeJob(
                    storyId = story.id,
                    sourceObjectKey = story.sourceObjectKey,
                    outputPrefix = "$videoStoryPrefix/${story.id}/published/"
                )
            )
        }
        return response(story, accountId)
    }

    @Transactional(readOnly = true)
    override fun get(viewerAccountId: UUID?, storyId: UUID): StoryResponse {
        val story = find(storyId)
        if (!mayView(viewerAccountId, story)) notFound()
        return response(story, viewerAccountId)
    }

    @Transactional(readOnly = true)
    override fun visible(viewerAccountId: UUID?, cursor: String?, limit: Int): StoryPageResponse =
        visiblePage(viewerAccountId, cursor, limit) { decoded, pageable ->
            storyRepository.findVisibleStories(
                viewerAccountId = viewerAccountId,
                publishedStatus = StoryStatus.PUBLISHED,
                contentPublishedStatus = ContentStatus.PUBLISHED,
                everyone = StoryVisibility.EVERYONE,
                followers = StoryVisibility.FOLLOWERS,
                closeFriends = StoryVisibility.CLOSE_FRIENDS,
                activeStatus = AccountStatus.ACTIVE,
                now = Instant.now(),
                mutedOwnerAccountIds = contentMuteService
                    .mutedAccountIds(viewerAccountId, ContentMuteType.STORY)
                    .ifEmpty { setOf(NO_ACCOUNT) },
                cursorSeenTier = decoded.seenTier,
                before = decoded.before,
                cursorId = decoded.id,
                pageable = pageable
            )
        }

    @Transactional(readOnly = true)
    override fun visibleByHandle(
        viewerAccountId: UUID?,
        handle: String,
        cursor: String?,
        limit: Int
    ): StoryPageResponse {
        val ownerAccountId = profileService.getByHandle(handle, viewerAccountId).accountId
        return visiblePage(viewerAccountId, cursor, limit) { decoded, pageable ->
            storyRepository.findVisibleStoriesByOwner(
                viewerAccountId = viewerAccountId,
                ownerAccountId = ownerAccountId,
                publishedStatus = StoryStatus.PUBLISHED,
                contentPublishedStatus = ContentStatus.PUBLISHED,
                everyone = StoryVisibility.EVERYONE,
                followers = StoryVisibility.FOLLOWERS,
                closeFriends = StoryVisibility.CLOSE_FRIENDS,
                activeStatus = AccountStatus.ACTIVE,
                now = Instant.now(),
                cursorSeenTier = decoded.seenTier,
                before = decoded.before,
                cursorId = decoded.id,
                pageable = pageable
            )
        }
    }

    @Transactional(readOnly = true)
    override fun archive(ownerAccountId: UUID, cursor: String?, limit: Int): StoryPageResponse {
        val pageSize = limit.coerceIn(1, 50)
        val pageable = PageRequest.of(0, pageSize + 1)
        val stories = cursor?.let(ArchiveCursor::decode)?.let {
            storyRepository.findArchiveAfter(
                ownerAccountId,
                StoryStatus.PUBLISHED,
                it.publishedAt,
                it.id,
                pageable
            )
        } ?: storyRepository.findArchive(ownerAccountId, StoryStatus.PUBLISHED, pageable)
        val page = stories.take(pageSize)
        return StoryPageResponse(
            items = responses(page, ownerAccountId, fullDownloadTtl = true),
            nextCursor = page.lastOrNull()
                ?.takeIf { stories.size > pageSize }
                ?.let(ArchiveCursor::from)
                ?.encode()
        )
    }

    @Transactional
    override fun createHighlight(
        ownerAccountId: UUID,
        request: UpsertStoryHighlightRequest
    ): StoryHighlightResponse {
        requireActive(ownerAccountId)
        val name = validatedHighlightName(request)
        val stories = highlightableStories(ownerAccountId, request.storyIds)
        val highlight = storyHighlightRepository.save(
            StoryHighlight(ownerAccountId = ownerAccountId, name = name)
        )
        storyHighlightItemRepository.saveAll(highlightItems(highlight.id, stories))
        return highlightResponse(highlight, stories, ownerAccountId)
    }

    @Transactional
    override fun updateHighlight(
        ownerAccountId: UUID,
        highlightId: UUID,
        request: UpsertStoryHighlightRequest
    ): StoryHighlightResponse {
        val name = validatedHighlightName(request)
        val stories = highlightableStories(ownerAccountId, request.storyIds)
        val highlight = ownedHighlight(highlightId, ownerAccountId)
        highlight.name = name
        highlight.updatedAt = Instant.now()
        storyHighlightItemRepository.deleteByHighlightId(highlight.id)
        storyHighlightItemRepository.saveAll(highlightItems(highlight.id, stories))
        return highlightResponse(highlight, stories, ownerAccountId)
    }

    @Transactional
    override fun deleteHighlight(ownerAccountId: UUID, highlightId: UUID) {
        storyHighlightRepository.delete(ownedHighlight(highlightId, ownerAccountId))
    }

    @Transactional(readOnly = true)
    override fun highlightsByHandle(
        viewerAccountId: UUID?,
        handle: String,
        cursor: String?,
        limit: Int
    ): StoryHighlightPageResponse {
        val profile = profileRepository.findByHandle(handle.trim().lowercase(Locale.ROOT)).orElseThrow {
            profileNotFound()
        }
        val audience = highlightAudience(profile.accountId, profile.isPublic, viewerAccountId)
        val decoded = cursor?.let(HighlightCursor::decode)
        val pageSize = limit.coerceIn(1, 50)
        val pageable = PageRequest.of(0, pageSize + 1)
        val highlights = if (audience.isOwner) {
            decoded?.let {
                storyHighlightRepository.findByOwnerAfter(
                    profile.accountId,
                    it.createdAt,
                    it.id,
                    pageable
                )
            } ?: storyHighlightRepository.findByOwnerAccountIdOrderByCreatedAtDescIdDesc(
                profile.accountId,
                pageable
            )
        } else {
            decoded?.let {
                storyHighlightRepository.findVisibleByOwnerAfter(
                    profile.accountId,
                    StoryStatus.PUBLISHED,
                    ContentStatus.PUBLISHED,
                    audience.visibilities,
                    it.createdAt,
                    it.id,
                    pageable
                )
            } ?: storyHighlightRepository.findVisibleByOwner(
                profile.accountId,
                StoryStatus.PUBLISHED,
                ContentStatus.PUBLISHED,
                audience.visibilities,
                pageable
            )
        }
        val page = highlights.take(pageSize)
        return StoryHighlightPageResponse(
            items = highlightSummaries(page, audience, viewerAccountId),
            nextCursor = page.lastOrNull()
                ?.takeIf { highlights.size > pageSize }
                ?.let(HighlightCursor::from)
                ?.encode()
        )
    }

    @Transactional(readOnly = true)
    override fun highlight(viewerAccountId: UUID?, highlightId: UUID): StoryHighlightResponse {
        val highlight = storyHighlightRepository.findById(highlightId).orElseThrow { highlightNotFound() }
        val profile = profileRepository.findByAccountId(highlight.ownerAccountId).orElseThrow {
            highlightNotFound()
        }
        val audience = highlightAudience(highlight.ownerAccountId, profile.isPublic, viewerAccountId)
        val storyIds = storyHighlightItemRepository.findByHighlightIdOrderByPositionAsc(highlight.id)
            .map(StoryHighlightItem::storyId)
        val stories = visibleHighlightStories(storyIds, audience)
        if (!audience.isOwner && stories.isEmpty()) highlightNotFound()
        return highlightResponse(highlight, stories, viewerAccountId)
    }

    @Transactional
    override fun markSeen(viewerAccountId: UUID, storyId: UUID): StorySeenResponse {
        val story = find(storyId)
        ensureVisible(viewerAccountId, storyId)
        val now = Instant.now()
        if (story.ownerAccountId == viewerAccountId) {
            return StorySeenResponse(story.id, hasSeen = true, seenAt = now)
        }
        val view = storyViewRepository.findByStoryIdAndViewerAccountId(story.id, viewerAccountId)
            ?: storyViewRepository.save(
                StoryView(storyId = story.id, viewerAccountId = viewerAccountId, viewedAt = now)
            )
        return StorySeenResponse(story.id, hasSeen = true, seenAt = view.viewedAt)
    }

    @Transactional(readOnly = true)
    override fun viewers(
        ownerAccountId: UUID,
        storyId: UUID,
        cursor: String?,
        limit: Int
    ): ProfileSummaryPageResponse {
        val story = owned(storyId, ownerAccountId)
        if (story.status != StoryStatus.PUBLISHED) notFound()
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val views = cursor?.let(ViewCursor::decode)?.let {
            storyViewRepository.findViewsAfter(storyId, it.viewedAt, it.id, pageable)
        } ?: storyViewRepository.findByStoryIdOrderByViewedAtDescIdDesc(storyId, pageable)
        val page = views.take(pageSize)
        val accountIds = page.map(StoryView::viewerAccountId)
        val profiles = profileService.summaries(accountIds.toSet())
        return ProfileSummaryPageResponse(
            items = accountIds.mapNotNull(profiles::get),
            nextCursor = page.lastOrNull()
                ?.takeIf { views.size > pageSize }
                ?.let(ViewCursor::from)
                ?.encode()
        )
    }

    @Transactional(readOnly = true)
    override fun ensureVisible(viewerAccountId: UUID?, storyId: UUID) {
        val story = find(storyId)
        if (mayView(viewerAccountId, story)) return
        val visibleInHighlight = story.status == StoryStatus.PUBLISHED &&
            storyHighlightItemRepository.existsByStoryId(story.id) &&
            mayViewPublishedIgnoringExpiry(viewerAccountId, story)
        if (!visibleInHighlight) notFound()
    }

    @Transactional
    override fun delete(accountId: UUID, storyId: UUID) {
        val story = owned(storyId, accountId)
        if (story.status != StoryStatus.DELETED) {
            story.status = StoryStatus.DELETED
            story.updatedAt = Instant.now()
        }
    }

    private fun validatedHighlightName(request: UpsertStoryHighlightRequest): String {
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Highlight name must not be blank" }
        require(name.length <= 80) { "Highlight name must not exceed 80 characters" }
        require(request.storyIds.isNotEmpty()) { "A highlight must contain at least one story" }
        require(request.storyIds.size <= 100) { "A highlight may contain at most 100 stories" }
        require(request.storyIds.distinct().size == request.storyIds.size) {
            "A story may appear only once in a highlight"
        }
        return name
    }

    private fun highlightableStories(ownerAccountId: UUID, storyIds: List<UUID>): List<Story> {
        val storiesById = storyRepository.findAllById(storyIds).associateBy(Story::id)
        if (storiesById.size != storyIds.size) storyNotAvailableForHighlight()
        return storyIds.map { storyId ->
            requireNotNull(storiesById[storyId]).also { story ->
                if (
                    story.ownerAccountId != ownerAccountId ||
                    story.status != StoryStatus.PUBLISHED ||
                    story.publishedAt == null ||
                    story.publishedObjectKey == null ||
                    !sharedSourceAvailable(story)
                ) {
                    storyNotAvailableForHighlight()
                }
            }
        }
    }

    private fun highlightItems(highlightId: UUID, stories: List<Story>): List<StoryHighlightItem> =
        stories.mapIndexed { position, story ->
            StoryHighlightItem(highlightId = highlightId, storyId = story.id, position = position)
        }

    private fun ownedHighlight(highlightId: UUID, ownerAccountId: UUID): StoryHighlight =
        storyHighlightRepository.findById(highlightId).orElseThrow { highlightNotFound() }.also {
            if (it.ownerAccountId != ownerAccountId) highlightNotFound()
        }

    private fun highlightAudience(
        ownerAccountId: UUID,
        ownerIsPublic: Boolean,
        viewerAccountId: UUID?
    ): HighlightAudience {
        if (ownerAccountId == viewerAccountId) {
            return HighlightAudience(
                ownerAccountId = ownerAccountId,
                isOwner = true,
                visibilities = StoryVisibility.entries.toSet()
            )
        }
        if (!isActive(ownerAccountId)) highlightNotFound()
        val followsOwner = viewerAccountId != null &&
            socialGraphService.isFollowing(viewerAccountId, ownerAccountId)
        if (!ownerIsPublic && !followsOwner) highlightNotFound()
        val isCloseFriend = viewerAccountId != null &&
            isActive(viewerAccountId) &&
            closeFriendRepository.existsByOwnerAccountIdAndFriendAccountId(
                ownerAccountId,
                viewerAccountId
            )
        return HighlightAudience(
            ownerAccountId = ownerAccountId,
            isOwner = false,
            visibilities = buildSet {
                add(StoryVisibility.EVERYONE)
                if (followsOwner) add(StoryVisibility.FOLLOWERS)
                if (isCloseFriend) add(StoryVisibility.CLOSE_FRIENDS)
            }
        )
    }

    private fun highlightSummaries(
        highlights: List<StoryHighlight>,
        audience: HighlightAudience,
        viewerAccountId: UUID?
    ): List<StoryHighlightSummaryResponse> {
        if (highlights.isEmpty()) return emptyList()
        val itemsByHighlight = storyHighlightItemRepository
            .findByHighlightIdInOrderByHighlightIdAscPositionAsc(highlights.map(StoryHighlight::id))
            .groupBy(StoryHighlightItem::highlightId)
        val visibleStories = visibleHighlightStories(
            itemsByHighlight.values.flatten().map(StoryHighlightItem::storyId).distinct(),
            audience
        )
        val responseByStoryId = responses(visibleStories, viewerAccountId, fullDownloadTtl = true)
            .associateBy(StoryResponse::id)
        return highlights.map { highlight ->
            val storyResponses = itemsByHighlight[highlight.id].orEmpty()
                .mapNotNull { responseByStoryId[it.storyId] }
            StoryHighlightSummaryResponse(
                id = highlight.id,
                name = highlight.name,
                coverStory = storyResponses.firstOrNull(),
                storyCount = storyResponses.size,
                createdAt = highlight.createdAt,
                updatedAt = highlight.updatedAt
            )
        }
    }

    private fun highlightResponse(
        highlight: StoryHighlight,
        stories: List<Story>,
        viewerAccountId: UUID?
    ) = StoryHighlightResponse(
        id = highlight.id,
        name = highlight.name,
        stories = responses(stories, viewerAccountId, fullDownloadTtl = true),
        createdAt = highlight.createdAt,
        updatedAt = highlight.updatedAt
    )

    private fun visibleHighlightStories(
        storyIds: List<UUID>,
        audience: HighlightAudience
    ): List<Story> {
        if (storyIds.isEmpty()) return emptyList()
        val storiesById = storyRepository.findAllById(storyIds).associateBy(Story::id)
        return storyIds.mapNotNull { storiesById[it] }.filter { story ->
            story.ownerAccountId == audience.ownerAccountId &&
                story.status == StoryStatus.PUBLISHED &&
                story.publishedAt != null &&
                story.publishedObjectKey != null &&
                story.visibility in audience.visibilities &&
                sharedSourceAvailable(story)
        }
    }

    private fun uploadedStory(
        storyId: UUID,
        accountId: UUID,
        request: CreateStoryRequest,
        media: CreateStoryMediaRequest
    ): Story {
        val mediaType = when (media.contentType) {
            in imageContentTypes -> StoryMediaType.IMAGE
            in videoContentTypes -> StoryMediaType.VIDEO
            else -> throw IllegalArgumentException(
                "A story accepts JPEG, PNG, WebP, MP4, QuickTime, or WebM media"
            )
        }
        val maxBytes = if (mediaType == StoryMediaType.IMAGE) {
            properties.maxStoryImageBytes
        } else {
            properties.maxStoryVideoBytes
        }
        require(media.sizeBytes <= maxBytes) { "Story media exceeds the configured upload limit" }

        val extension = media.fileName.substringAfterLast('.', "bin").lowercase().take(8)
        val prefix = if (mediaType == StoryMediaType.IMAGE) imageStoryPrefix else videoStoryPrefix
        return Story(
            id = storyId,
            ownerAccountId = accountId,
            mediaType = mediaType,
            visibility = request.visibility,
            caption = request.caption?.trim()?.takeIf(String::isNotEmpty),
            sourceObjectKey = "$prefix/$storyId/source/${UUID.randomUUID()}.$extension",
            contentType = media.contentType,
            sourceSizeBytes = media.sizeBytes,
            linkedContentId = request.linkedContentId,
            ownsMedia = true
        )
    }

    private fun linkedContentStory(
        storyId: UUID,
        accountId: UUID,
        request: CreateStoryRequest,
        content: ContentItem
    ): Story {
        val now = Instant.now()
        val media = when (content) {
            is Post -> {
                val first = postMediaRepository.findByPostIdOrderByPositionAsc(content.id).firstOrNull()
                    ?: throw IllegalArgumentException("Linked post has no media")
                LinkedMedia(
                    type = StoryMediaType.IMAGE,
                    objectKey = first.objectKey,
                    thumbnailObjectKey = null,
                    contentType = first.contentType,
                    sizeBytes = first.sizeBytes,
                    durationSeconds = null
                )
            }
            is Reel -> LinkedMedia(
                type = StoryMediaType.VIDEO,
                objectKey = content.publishedObjectKey
                    ?: throw IllegalArgumentException("Linked reel has no published video"),
                thumbnailObjectKey = content.thumbnailObjectKey,
                contentType = "video/mp4",
                sizeBytes = content.sourceSizeBytes,
                durationSeconds = content.durationSeconds
            )
            else -> error("Unsupported linked content type")
        }
        return Story(
            id = storyId,
            ownerAccountId = accountId,
            mediaType = media.type,
            visibility = request.visibility,
            status = StoryStatus.PUBLISHED,
            caption = request.caption?.trim()?.takeIf(String::isNotEmpty),
            sourceObjectKey = media.objectKey,
            publishedObjectKey = media.objectKey,
            thumbnailObjectKey = media.thumbnailObjectKey,
            contentType = media.contentType,
            sourceSizeBytes = media.sizeBytes,
            durationSeconds = media.durationSeconds,
            linkedContentId = content.id,
            ownsMedia = false,
            publishedAt = now,
            expiresAt = now.plus(STORY_LIFETIME)
        )
    }

    private fun resharedStory(
        storyId: UUID,
        accountId: UUID,
        request: CreateStoryRequest,
        source: Story
    ): Story {
        val now = Instant.now()
        return Story(
            id = storyId,
            ownerAccountId = accountId,
            mediaType = source.mediaType,
            visibility = request.visibility,
            status = StoryStatus.PUBLISHED,
            caption = request.caption?.trim()?.takeIf(String::isNotEmpty),
            sourceObjectKey = source.sourceObjectKey,
            publishedObjectKey = requireNotNull(source.publishedObjectKey),
            thumbnailObjectKey = source.thumbnailObjectKey,
            contentType = source.contentType,
            sourceSizeBytes = source.sourceSizeBytes,
            durationSeconds = source.durationSeconds,
            linkedStoryId = source.id,
            ownsMedia = false,
            publishedAt = now,
            expiresAt = now.plus(STORY_LIFETIME)
        )
    }

    private fun visiblePage(
        viewerAccountId: UUID?,
        cursor: String?,
        limit: Int,
        query: (StoryCursor, PageRequest) -> List<Story>
    ): StoryPageResponse {
        val pageSize = limit.coerceIn(1, 50)
        val decoded = cursor?.let(StoryCursor::decode) ?: StoryCursor.initial()
        val fetched = query(decoded, PageRequest.of(0, pageSize + 1))
        val page = fetched.take(pageSize)
        val items = responses(page, viewerAccountId)
        return StoryPageResponse(
            items = items,
            nextCursor = page.lastOrNull()
                ?.takeIf { fetched.size > pageSize }
                ?.let { last -> StoryCursor.from(last, requireNotNull(items.lastOrNull()).hasSeen) }
                ?.encode()
        )
    }

    private fun responses(
        stories: List<Story>,
        viewerAccountId: UUID?,
        fullDownloadTtl: Boolean = false
    ): List<StoryResponse> {
        if (stories.isEmpty()) return emptyList()
        val authors = profileService.summaries(stories.map(Story::ownerAccountId).toSet())
        val mentions = storyMentionRepository.findByStoryIdIn(stories.map(Story::id))
            .groupBy(StoryMention::storyId)
        val taggedProfiles = profileService.summaries(
            mentions.values.flatten().map(StoryMention::mentionedAccountId).toSet()
        )
        val linkedContent = contentRepository.findAllById(stories.mapNotNull(Story::linkedContentId).toSet())
            .filter { it.status == ContentStatus.PUBLISHED }
            .associateBy(ContentItem::id)
        val seenStoryIds = seenStoryIds(stories, viewerAccountId)
        val storyIds = stories.map(Story::id)
        val likeCounts = contentLikeRepository.countByStoryIds(storyIds)
            .associate { it.storyId to it.likeCount }
        val commentCounts = contentCommentRepository.countByStoryIds(storyIds)
            .associate { it.storyId to it.commentCount }
        val likedStoryIds = if (viewerAccountId == null) emptySet() else
            contentLikeRepository.findLikedStoryIds(viewerAccountId, storyIds)

        return stories.map { story ->
            val author = authors[story.ownerAccountId] ?: notFound()
            val tagged = mentions[story.id].orEmpty().mapNotNull {
                taggedProfiles[it.mentionedAccountId]
            }
            response(
                story = story,
                viewerAccountId = viewerAccountId,
                taggedAccounts = tagged,
                linkedContent = linkedContent[story.linkedContentId],
                author = author,
                hasSeen = story.ownerAccountId == viewerAccountId || story.id in seenStoryIds,
                likeCount = likeCounts[story.id] ?: 0,
                commentCount = commentCounts[story.id] ?: 0,
                viewerLiked = story.id in likedStoryIds,
                fullDownloadTtl = fullDownloadTtl
            )
        }
    }

    private fun response(
        story: Story,
        viewerAccountId: UUID?,
        taggedAccounts: List<ProfileSummaryResponse> = taggedAccounts(story),
        linkedContent: ContentItem? = story.linkedContentId?.let { id ->
            contentRepository.findById(id).orElse(null)?.takeIf { it.status == ContentStatus.PUBLISHED }
        },
        author: ProfileSummaryResponse = profileService.summaries(setOf(story.ownerAccountId))[story.ownerAccountId]
            ?: notFound(),
        hasSeen: Boolean = hasSeen(story, viewerAccountId),
        likeCount: Long = contentLikeRepository.countByStoryId(story.id),
        commentCount: Long = contentCommentRepository.countByStoryId(story.id),
        viewerLiked: Boolean = viewerAccountId != null &&
            contentLikeRepository.existsByStoryIdAndAccountId(story.id, viewerAccountId),
        fullDownloadTtl: Boolean = false
    ): StoryResponse {
        val ttl = if (fullDownloadTtl) storageProperties.downloadUrlTtl else storyDownloadTtl(story)
        val availableLinkedContent = linkedContent?.takeIf {
            contentAvailableForShare(story.ownerAccountId, it)
        }
        val linked = availableLinkedContent?.let {
            StoryLinkedContentResponse(it.id, contentKind(it))
        }
        val linkedStoryAvailable = story.linkedStoryId?.let {
            linkedStorySourceAvailable(story.ownerAccountId, it)
        }
        val mediaAvailable = story.ownsMedia || availableLinkedContent != null || linkedStoryAvailable == true
        val linkedStory = story.linkedStoryId?.let {
            StoryLinkedStoryResponse(
                storyId = it,
                available = linkedStoryAvailable == true,
                unavailableMessage = if (linkedStoryAvailable == true) null else STORY_UNAVAILABLE_MESSAGE
            )
        }
        val owner = viewerAccountId == story.ownerAccountId
        return StoryResponse(
            id = story.id,
            author = author,
            mediaType = story.mediaType,
            visibility = story.visibility,
            status = story.status,
            caption = story.caption,
            mediaUrl = story.publishedObjectKey?.takeIf { mediaAvailable }?.let {
                storage.createDownloadUrl(it, ttl)
            },
            thumbnailUrl = story.thumbnailObjectKey?.takeIf { mediaAvailable }?.let {
                storage.createDownloadUrl(it, ttl)
            },
            durationSeconds = story.durationSeconds,
            failureReason = story.failureReason,
            moderationReason = story.moderationReason,
            likeCount = likeCount,
            liked = viewerLiked,
            commentCount = commentCount,
            linkedContent = linked,
            taggedAccounts = taggedAccounts,
            publishedAt = story.publishedAt,
            expiresAt = story.expiresAt,
            expired = story.expiresAt?.let { !it.isAfter(Instant.now()) } ?: false,
            createdAt = story.createdAt,
            hasSeen = hasSeen,
            viewerState = StoryViewerStateResponse(isOwner = owner, canDelete = owner),
            linkedStory = linkedStory
        )
    }

    private fun hasSeen(story: Story, viewerAccountId: UUID?): Boolean = when {
        viewerAccountId == null -> false
        story.ownerAccountId == viewerAccountId -> true
        else -> storyViewRepository.findByStoryIdAndViewerAccountId(story.id, viewerAccountId) != null
    }

    private fun seenStoryIds(stories: List<Story>, viewerAccountId: UUID?): Set<UUID> {
        if (viewerAccountId == null || stories.isEmpty()) return emptySet()
        return storyViewRepository.findByStoryIdInAndViewerAccountId(
            stories.map(Story::id),
            viewerAccountId
        ).mapTo(mutableSetOf(), StoryView::storyId)
    }

    private fun taggedAccounts(story: Story): List<ProfileSummaryResponse> {
        val accountIds = storyMentionRepository.findByStoryIdIn(listOf(story.id))
            .map(StoryMention::mentionedAccountId)
        val profiles = profileService.summaries(accountIds.toSet())
        return accountIds.mapNotNull(profiles::get)
    }

    private fun mayView(viewerAccountId: UUID?, story: Story): Boolean {
        val now = Instant.now()
        val owner = viewerAccountId == story.ownerAccountId
        if (owner) {
            return story.status != StoryStatus.DELETED && (story.expiresAt == null || story.expiresAt!!.isAfter(now))
        }
        if (story.status != StoryStatus.PUBLISHED || story.expiresAt?.isAfter(now) != true) return false
        return mayViewPublishedIgnoringExpiry(viewerAccountId, story)
    }

    private fun mayViewPublishedIgnoringExpiry(viewerAccountId: UUID?, story: Story): Boolean {
        if (story.status != StoryStatus.PUBLISHED) return false
        if (story.publishedObjectKey == null) return false
        if (!sharedSourceAvailable(story)) return false
        if (viewerAccountId == story.ownerAccountId) return true
        if (!isActive(story.ownerAccountId)) return false
        val followsOwner = viewerAccountId != null &&
            socialGraphService.isFollowing(viewerAccountId, story.ownerAccountId)
        val ownerIsPublic = profileRepository.findByAccountId(story.ownerAccountId)
            .map { it.isPublic }
            .orElse(false)
        if (!ownerIsPublic && !followsOwner) return false
        return when (story.visibility) {
            StoryVisibility.EVERYONE -> true
            StoryVisibility.FOLLOWERS -> followsOwner
            StoryVisibility.CLOSE_FRIENDS -> viewerAccountId != null &&
                isActive(viewerAccountId) &&
                closeFriendRepository.existsByOwnerAccountIdAndFriendAccountId(
                    story.ownerAccountId,
                    viewerAccountId
                )
        }
    }

    private fun sharedSourceAvailable(story: Story): Boolean = when {
        story.ownsMedia -> true
        story.linkedContentId != null -> contentRepository.findById(story.linkedContentId!!)
            .orElse(null)
            ?.let { contentAvailableForShare(story.ownerAccountId, it) }
            ?: false
        story.linkedStoryId != null -> linkedStorySourceAvailable(story.ownerAccountId, story.linkedStoryId!!)
        else -> false
    }

    private fun linkedStorySourceAvailable(storyOwnerAccountId: UUID, linkedStoryId: UUID): Boolean =
        storyRepository.findById(linkedStoryId).orElse(null)?.let {
            storyAvailableForShare(storyOwnerAccountId, it, mutableSetOf())
        } ?: false

    private fun storyAvailableForShare(
        storyOwnerAccountId: UUID,
        source: Story,
        visitedStoryIds: MutableSet<UUID>
    ): Boolean {
        if (!visitedStoryIds.add(source.id)) return false
        if (source.status != StoryStatus.PUBLISHED) return false
        if (source.expiresAt?.isAfter(Instant.now()) != true || source.publishedObjectKey == null) return false
        if (source.ownerAccountId != storyOwnerAccountId) {
            if (!isActive(source.ownerAccountId)) return false
            if (source.visibility != StoryVisibility.EVERYONE) return false
            if (profileRepository.findByAccountId(source.ownerAccountId).orElse(null)?.isPublic != true) return false
        }
        return when {
            source.ownsMedia -> true
            source.linkedContentId != null -> contentRepository.findById(source.linkedContentId!!)
                .orElse(null)
                ?.let { contentAvailableForShare(source.ownerAccountId, it) }
                ?: false
            source.linkedStoryId != null -> storyRepository.findById(source.linkedStoryId!!)
                .orElse(null)
                ?.let { storyAvailableForShare(storyOwnerAccountId, it, visitedStoryIds) }
                ?: false
            else -> false
        }
    }

    private fun shareablePublishedContent(accountId: UUID, contentId: UUID): ContentItem =
        contentRepository.findById(contentId).orElseThrow { contentNotFound() }.also {
            if (!contentAvailableForShare(accountId, it)) contentNotFound()
        }

    private fun contentAvailableForShare(accountId: UUID, content: ContentItem): Boolean {
        if (content.status != ContentStatus.PUBLISHED) return false
        if (content.ownerAccountId == accountId) return true
        if (!isActive(content.ownerAccountId)) return false
        return profileRepository.findByAccountId(content.ownerAccountId).orElse(null)?.isPublic == true
    }

    private fun shareableStorySource(accountId: UUID, storyId: UUID): Story {
        val requested = storyRepository.findById(storyId).orElseThrow { notFound() }
        if (!storyAvailableForShare(accountId, requested, mutableSetOf())) notFound()
        return rootStorySource(requested).also {
            if (!storyAvailableForShare(accountId, it, mutableSetOf())) notFound()
        }
    }

    private fun rootStorySource(story: Story): Story {
        var current = story
        val visited = mutableSetOf<UUID>()
        while (current.linkedStoryId != null) {
            if (!visited.add(current.id)) notFound()
            current = storyRepository.findById(requireNotNull(current.linkedStoryId)).orElseThrow { notFound() }
        }
        return current
    }

    private fun contentKind(content: ContentItem): ContentKind = when (content) {
        is Post -> ContentKind.POST
        is Reel -> ContentKind.REEL
        else -> error("Unsupported content type")
    }

    private fun owned(storyId: UUID, accountId: UUID): Story = find(storyId).also {
        if (it.ownerAccountId != accountId) notFound()
    }

    private fun find(storyId: UUID): Story = storyRepository.findById(storyId).orElseThrow { notFound() }

    private fun requireActive(accountId: UUID) {
        if (!isActive(accountId)) {
            throw IdentityConflictException("Account must be active to create stories")
        }
    }

    private fun isActive(accountId: UUID): Boolean =
        accountRepository.findById(accountId).orElse(null)?.status == AccountStatus.ACTIVE

    private fun storyDownloadTtl(story: Story): Duration {
        val expiresAt = story.expiresAt ?: return storageProperties.downloadUrlTtl
        val remaining = Duration.between(Instant.now(), expiresAt)
        if (remaining.isZero || remaining.isNegative) return Duration.ofSeconds(1)
        return if (remaining < storageProperties.downloadUrlTtl) remaining else storageProperties.downloadUrlTtl
    }

    private val imageStoryPrefix: String
        get() = "${storageProperties.imagePrefix.trim('/')}/stories"

    private val videoStoryPrefix: String
        get() = "${storageProperties.videoPrefix.trim('/')}/stories"

    private fun notFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Story was not found")

    private fun highlightNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Highlight was not found")

    private fun profileNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Profile was not found")

    private fun storyNotAvailableForHighlight(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Story is not available for this highlight")

    private fun contentNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Linked content was not found")

    private data class LinkedMedia(
        val type: StoryMediaType,
        val objectKey: String,
        val thumbnailObjectKey: String?,
        val contentType: String,
        val sizeBytes: Long,
        val durationSeconds: Int?
    )

    private data class StoryCursor(val seenTier: Int, val before: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$seenTier|$before|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun initial() = StoryCursor(UNSEEN_TIER, Instant.now().plusMillis(1), MAX_UUID)

            fun from(story: Story, hasSeen: Boolean) = StoryCursor(
                seenTier = if (hasSeen) SEEN_TIER else UNSEEN_TIER,
                before = requireNotNull(story.publishedAt),
                id = story.id
            )

            fun decode(value: String): StoryCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid story cursor") }
                val parts = decoded.split('|')
                require(parts.size == 2 || parts.size == 3) { "Invalid story cursor" }
                val hasTier = parts.size == 3
                val seenTier = if (hasTier) {
                    parts[0].toIntOrNull()
                        ?.takeIf { it == UNSEEN_TIER || it == SEEN_TIER }
                        ?: throw IllegalArgumentException("Invalid story cursor")
                } else {
                    UNSEEN_TIER
                }
                val beforeIndex = if (hasTier) 1 else 0
                val idIndex = if (hasTier) 2 else 1
                return StoryCursor(
                    seenTier = seenTier,
                    before = runCatching { Instant.parse(parts[beforeIndex]) }
                        .getOrElse { throw IllegalArgumentException("Invalid story cursor") },
                    id = runCatching { UUID.fromString(parts[idIndex]) }
                        .getOrElse { throw IllegalArgumentException("Invalid story cursor") }
                )
            }
        }
    }

    private data class ArchiveCursor(val publishedAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$publishedAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(story: Story) = ArchiveCursor(requireNotNull(story.publishedAt), story.id)

            fun decode(value: String): ArchiveCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid story archive cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid story archive cursor" }
                return ArchiveCursor(
                    publishedAt = runCatching { Instant.parse(parts[0]) }
                        .getOrElse { throw IllegalArgumentException("Invalid story archive cursor") },
                    id = runCatching { UUID.fromString(parts[1]) }
                        .getOrElse { throw IllegalArgumentException("Invalid story archive cursor") }
                )
            }
        }
    }

    private data class HighlightCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$createdAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(highlight: StoryHighlight) = HighlightCursor(highlight.createdAt, highlight.id)

            fun decode(value: String): HighlightCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid story highlight cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid story highlight cursor" }
                return HighlightCursor(
                    createdAt = runCatching { Instant.parse(parts[0]) }
                        .getOrElse { throw IllegalArgumentException("Invalid story highlight cursor") },
                    id = runCatching { UUID.fromString(parts[1]) }
                        .getOrElse { throw IllegalArgumentException("Invalid story highlight cursor") }
                )
            }
        }
    }

    private data class ViewCursor(val viewedAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$viewedAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(view: StoryView) = ViewCursor(view.viewedAt, view.id)

            fun decode(value: String): ViewCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid story viewer cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid story viewer cursor" }
                return ViewCursor(
                    viewedAt = runCatching { Instant.parse(parts[0]) }
                        .getOrElse { throw IllegalArgumentException("Invalid story viewer cursor") },
                    id = runCatching { UUID.fromString(parts[1]) }
                        .getOrElse { throw IllegalArgumentException("Invalid story viewer cursor") }
                )
            }
        }
    }

    private data class HighlightAudience(
        val ownerAccountId: UUID,
        val isOwner: Boolean,
        val visibilities: Set<StoryVisibility>
    )

    private companion object {
        val STORY_LIFETIME: Duration = Duration.ofHours(24)
        const val STORY_UNAVAILABLE_MESSAGE = "Story not available anymore"
        const val UNSEEN_TIER = 0
        const val SEEN_TIER = 1
        val NO_ACCOUNT: UUID = UUID(0, 0)
        val MAX_UUID: UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val imageContentTypes = setOf("image/jpeg", "image/png", "image/webp")
        val videoContentTypes = setOf("video/mp4", "video/quicktime", "video/webm")
    }
}
