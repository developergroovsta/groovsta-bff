package com.groovsta.content.service.impl

import com.groovsta.content.dto.ContentFeedItemResponse
import com.groovsta.content.model.ContentKind
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.Post
import com.groovsta.content.model.Reel
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.repository.SavedContentRepository
import com.groovsta.content.service.PublishedContentPage
import com.groovsta.content.service.PostService
import com.groovsta.content.service.PublishedContentQueryService
import com.groovsta.content.service.ReelService
import com.groovsta.identity.service.ProfileService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class PublishedContentQueryServiceImpl(
    private val contentRepository: ContentItemRepository,
    private val savedContentRepository: SavedContentRepository,
    private val postService: PostService,
    private val reelService: ReelService,
    private val profileService: ProfileService
) : PublishedContentQueryService {

    @Transactional(readOnly = true)
    override fun homeFeed(
        viewerAccountId: UUID?,
        priorityOwnerIds: Set<UUID>,
        mutedPostOwnerIds: Set<UUID>,
        mutedReelOwnerIds: Set<UUID>,
        cursor: String?,
        limit: Int
    ): PublishedContentPage {
        val pageSize = limit.coerceIn(1, 50)
        val decodedCursor = cursor?.let(HomeCursor::decode)
        val content = mutableListOf<com.groovsta.content.model.ContentItem>()

        if (priorityOwnerIds.isNotEmpty() && decodedCursor?.tier != PUBLIC_TIER) {
            val priorityBefore = decodedCursor?.before ?: nextInstant()
            content += contentRepository.findPriorityContent(
                ContentStatus.PUBLISHED,
                priorityOwnerIds,
                mutedPostOwnerIds.ifEmpty { setOf(NO_ACCOUNT) },
                mutedReelOwnerIds.ifEmpty { setOf(NO_ACCOUNT) },
                priorityBefore,
                PageRequest.of(0, pageSize)
            )
            if (content.size == pageSize) {
                return page(content, PRIORITY_TIER, priorityOwnerIds, viewerAccountId)
            }
        }

        val remaining = pageSize - content.size
        if (remaining > 0) {
            val publicBefore = decodedCursor
                ?.takeIf { it.tier == PUBLIC_TIER }
                ?.before
                ?: nextInstant()
            val exclusions = priorityOwnerIds.ifEmpty { setOf(NO_ACCOUNT) }
            content += contentRepository.findPublicContentExcludingOwners(
                ContentStatus.PUBLISHED,
                exclusions,
                mutedPostOwnerIds.ifEmpty { setOf(NO_ACCOUNT) },
                mutedReelOwnerIds.ifEmpty { setOf(NO_ACCOUNT) },
                publicBefore,
                PageRequest.of(0, remaining)
            )
        }

        val lastTier = content.lastOrNull()?.let {
            if (it.ownerAccountId in priorityOwnerIds) PRIORITY_TIER else PUBLIC_TIER
        }
        return page(content, lastTier, priorityOwnerIds, viewerAccountId)
    }

    private fun page(
        content: List<com.groovsta.content.model.ContentItem>,
        lastTier: Int?,
        priorityOwnerIds: Set<UUID>,
        viewerAccountId: UUID?
    ): PublishedContentPage {
        val authors = profileService.summaries(content.map { it.ownerAccountId }.toSet())
        val postResponses = postService.getAll(
            viewerAccountId,
            content.filterIsInstance<Post>().map(Post::id)
        )
        val reelResponses = reelService.getAll(
            viewerAccountId,
            content.filterIsInstance<Reel>().map(Reel::id)
        )
        val savesByContentId = if (viewerAccountId == null || content.isEmpty()) {
            emptyMap()
        } else {
            savedContentRepository.findByAccountIdAndContentIdIn(
                viewerAccountId,
                content.map { it.id }
            ).associateBy { it.contentId }
        }
        val items = content.map { item ->
            val publishedAt = requireNotNull(item.publishedAt) { "Published content must have a publication time" }
            val author = requireNotNull(authors[item.ownerAccountId]) {
                "Published content owner must have a profile"
            }
            when (item) {
                is Post -> ContentFeedItemResponse(
                    id = item.id,
                    type = ContentKind.POST,
                    author = author,
                    isFollowingAuthor = followsAuthor(viewerAccountId, item.ownerAccountId, priorityOwnerIds),
                    canShareToStory = requireNotNull(postResponses[item.id]).canShareToStory,
                    saved = viewerAccountId?.let { item.id in savesByContentId },
                    savedAt = savesByContentId[item.id]?.createdAt,
                    post = requireNotNull(postResponses[item.id]),
                    reel = null,
                    publishedAt = publishedAt
                )
                is Reel -> ContentFeedItemResponse(
                    id = item.id,
                    type = ContentKind.REEL,
                    author = author,
                    isFollowingAuthor = followsAuthor(viewerAccountId, item.ownerAccountId, priorityOwnerIds),
                    canShareToStory = requireNotNull(reelResponses[item.id]).canShareToStory,
                    saved = viewerAccountId?.let { item.id in savesByContentId },
                    savedAt = savesByContentId[item.id]?.createdAt,
                    post = null,
                    reel = requireNotNull(reelResponses[item.id]),
                    publishedAt = publishedAt
                )
                else -> error("Unsupported content type: ${item::class.simpleName}")
            }
        }
        val nextCursor = content.lastOrNull()?.let { last ->
            HomeCursor(
                tier = lastTier ?: if (last.ownerAccountId in priorityOwnerIds) PRIORITY_TIER else PUBLIC_TIER,
                before = requireNotNull(last.publishedAt)
            ).encode()
        }
        return PublishedContentPage(items, nextCursor)
    }

    private fun followsAuthor(
        viewerAccountId: UUID?,
        authorAccountId: UUID,
        priorityOwnerIds: Set<UUID>
    ): Boolean? = viewerAccountId?.let {
        authorAccountId != viewerAccountId && authorAccountId in priorityOwnerIds
    }

    private data class HomeCursor(val tier: Int, val before: Instant) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$tier|$before".toByteArray(Charsets.UTF_8))

        companion object {
            fun decode(value: String): HomeCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid home feed cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid home feed cursor" }
                val tier = parts[0].toIntOrNull()
                require(tier == PRIORITY_TIER || tier == PUBLIC_TIER) { "Invalid home feed cursor" }
                val before = runCatching { Instant.parse(parts[1]) }
                    .getOrElse { throw IllegalArgumentException("Invalid home feed cursor") }
                return HomeCursor(tier, before)
            }
        }
    }

    private fun nextInstant(): Instant = Instant.now().plusMillis(1)

    private companion object {
        const val PRIORITY_TIER = 0
        const val PUBLIC_TIER = 1
        val NO_ACCOUNT: UUID = UUID(0, 0)
    }
}
