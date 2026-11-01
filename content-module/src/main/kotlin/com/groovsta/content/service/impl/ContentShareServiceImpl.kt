package com.groovsta.content.service.impl

import com.groovsta.content.dto.ShareResponse
import com.groovsta.content.dto.ShareTargetType
import com.groovsta.content.model.ContentShare
import com.groovsta.content.model.ContentShareChannel
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.StoryStatus
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.repository.ContentShareRepository
import com.groovsta.content.repository.StoryRepository
import com.groovsta.content.service.ContentShareService
import com.groovsta.identity.dto.response.ProfileSummaryPageResponse
import com.groovsta.identity.service.ProfileService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class ContentShareServiceImpl(
    private val shareRepository: ContentShareRepository,
    private val contentRepository: ContentItemRepository,
    private val storyRepository: StoryRepository,
    private val profileService: ProfileService
) : ContentShareService {

    @Transactional
    override fun recordContentShare(
        accountId: UUID,
        contentId: UUID,
        channel: ContentShareChannel
    ): ShareResponse {
        publishedContent(contentId)
        if (channel == ContentShareChannel.COPY_LINK) {
            return ShareResponse(contentId, ShareTargetType.CONTENT, false, countContentShares(contentId))
        }
        var shared = shareRepository.existsByContentIdAndAccountId(contentId, accountId)
        if (!shared) {
            try {
                shareRepository.saveAndFlush(
                    ContentShare(contentId = contentId, accountId = accountId, channel = channel)
                )
                shared = true
            } catch (_: DataIntegrityViolationException) {
                shared = true
            }
        }
        return ShareResponse(contentId, ShareTargetType.CONTENT, shared, countContentShares(contentId))
    }

    @Transactional
    override fun recordStoryShare(
        accountId: UUID,
        storyId: UUID,
        channel: ContentShareChannel
    ): ShareResponse {
        publishedStory(storyId)
        if (channel == ContentShareChannel.COPY_LINK) {
            return ShareResponse(storyId, ShareTargetType.STORY, false, countStoryShares(storyId))
        }
        var shared = shareRepository.existsByStoryIdAndAccountId(storyId, accountId)
        if (!shared) {
            try {
                shareRepository.saveAndFlush(
                    ContentShare(storyId = storyId, accountId = accountId, channel = channel)
                )
                shared = true
            } catch (_: DataIntegrityViolationException) {
                shared = true
            }
        }
        return ShareResponse(storyId, ShareTargetType.STORY, shared, countStoryShares(storyId))
    }

    @Transactional(readOnly = true)
    override fun contentSharers(contentId: UUID, cursor: String?, limit: Int): ProfileSummaryPageResponse {
        publishedContent(contentId)
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val shares = cursor?.let(ShareCursor::decode)?.let {
            shareRepository.findContentSharesAfter(contentId, it.createdAt, it.id, pageable)
        } ?: shareRepository.findByContentIdOrderByCreatedAtDescIdDesc(contentId, pageable)
        return sharerPage(shares, pageSize)
    }

    @Transactional(readOnly = true)
    override fun storySharers(storyId: UUID, cursor: String?, limit: Int): ProfileSummaryPageResponse {
        publishedStory(storyId)
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val shares = cursor?.let(ShareCursor::decode)?.let {
            shareRepository.findStorySharesAfter(storyId, it.createdAt, it.id, pageable)
        } ?: shareRepository.findByStoryIdOrderByCreatedAtDescIdDesc(storyId, pageable)
        return sharerPage(shares, pageSize)
    }

    @Transactional(readOnly = true)
    override fun countContentShares(contentId: UUID): Long = shareRepository.countByContentId(contentId)

    @Transactional(readOnly = true)
    override fun countStoryShares(storyId: UUID): Long = shareRepository.countByStoryId(storyId)

    @Transactional(readOnly = true)
    override fun countContentShares(contentIds: Collection<UUID>): Map<UUID, Long> =
        if (contentIds.isEmpty()) emptyMap()
        else shareRepository.countByContentIds(contentIds).associate { it.contentId to it.shareCount }

    @Transactional(readOnly = true)
    override fun countStoryShares(storyIds: Collection<UUID>): Map<UUID, Long> =
        if (storyIds.isEmpty()) emptyMap()
        else shareRepository.countByStoryIds(storyIds).associate { it.storyId to it.shareCount }

    private fun publishedContent(contentId: UUID) =
        contentRepository.findById(contentId).orElseThrow { notFound("Content") }.also {
            if (it.status != ContentStatus.PUBLISHED) throw notFound("Content")
        }

    private fun publishedStory(storyId: UUID) =
        storyRepository.findById(storyId).orElseThrow { notFound("Story") }.also {
            if (it.status != StoryStatus.PUBLISHED || it.expiresAt?.isAfter(Instant.now()) != true) {
                throw notFound("Story")
            }
        }

    private fun sharerPage(shares: List<ContentShare>, pageSize: Int): ProfileSummaryPageResponse {
        val page = shares.take(pageSize)
        val accountIds = page.map(ContentShare::accountId)
        val profiles = profileService.summaries(accountIds.toSet())
        return ProfileSummaryPageResponse(
            items = accountIds.mapNotNull(profiles::get),
            nextCursor = page.lastOrNull()
                ?.takeIf { shares.size > pageSize }
                ?.let(ShareCursor::from)
                ?.encode()
        )
    }

    private fun notFound(label: String): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "$label was not found")

    private data class ShareCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$createdAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(share: ContentShare) = ShareCursor(share.createdAt, share.id)

            fun decode(value: String): ShareCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid share cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid share cursor" }
                return ShareCursor(
                    createdAt = runCatching { Instant.parse(parts[0]) }
                        .getOrElse { throw IllegalArgumentException("Invalid share cursor") },
                    id = runCatching { UUID.fromString(parts[1]) }
                        .getOrElse { throw IllegalArgumentException("Invalid share cursor") }
                )
            }
        }
    }
}
