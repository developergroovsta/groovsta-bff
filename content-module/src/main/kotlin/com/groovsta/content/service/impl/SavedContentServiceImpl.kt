package com.groovsta.content.service.impl

import com.groovsta.content.dto.ContentFeedItemResponse
import com.groovsta.content.dto.SavedContentPageResponse
import com.groovsta.content.dto.SavedContentResponse
import com.groovsta.content.model.ContentKind
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.Post
import com.groovsta.content.model.Reel
import com.groovsta.content.model.SavedContent
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.repository.SavedContentRepository
import com.groovsta.content.service.PostService
import com.groovsta.content.service.ReelService
import com.groovsta.content.service.SavedContentService
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.ProfileService
import com.groovsta.identity.service.SocialGraphService
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class SavedContentServiceImpl(
    private val savedContentRepository: SavedContentRepository,
    private val contentRepository: ContentItemRepository,
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val profileService: ProfileService,
    private val socialGraphService: SocialGraphService,
    private val postService: PostService,
    private val reelService: ReelService
) : SavedContentService {

    @Transactional
    override fun save(accountId: UUID, contentId: UUID): SavedContentResponse {
        requireActive(accountId)
        val content = contentRepository.findById(contentId).orElseThrow { contentNotFound() }
        if (content.status != ContentStatus.PUBLISHED) contentNotFound()
        if (content.ownerAccountId == accountId) {
            throw IdentityConflictException("An account cannot save its own content")
        }
        if (!isActive(content.ownerAccountId)) contentNotFound()
        val ownerProfile = profileRepository.findByAccountId(content.ownerAccountId).orElseThrow {
            contentNotFound()
        }
        val mayView = ownerProfile.isPublic ||
            socialGraphService.isFollowing(accountId, content.ownerAccountId)
        if (!mayView) contentNotFound()

        val saved = savedContentRepository.findByAccountIdAndContentId(accountId, contentId)
            ?: savedContentRepository.save(SavedContent(accountId = accountId, contentId = contentId))
        return SavedContentResponse(contentId, saved = true, savedAt = saved.createdAt)
    }

    @Transactional
    override fun unsave(accountId: UUID, contentId: UUID): SavedContentResponse {
        savedContentRepository.deleteByAccountIdAndContentId(accountId, contentId)
        return SavedContentResponse(contentId, saved = false, savedAt = null)
    }

    @Transactional(readOnly = true)
    override fun savedPosts(accountId: UUID, cursor: String?, limit: Int): SavedContentPageResponse =
        page(accountId, cursor, limit) { decoded, pageable ->
            savedContentRepository.findVisibleSavedPosts(
                accountId,
                ContentStatus.PUBLISHED,
                AccountStatus.ACTIVE,
                decoded.createdAt,
                decoded.id,
                pageable
            )
        }

    @Transactional(readOnly = true)
    override fun savedReels(accountId: UUID, cursor: String?, limit: Int): SavedContentPageResponse =
        page(accountId, cursor, limit) { decoded, pageable ->
            savedContentRepository.findVisibleSavedReels(
                accountId,
                ContentStatus.PUBLISHED,
                AccountStatus.ACTIVE,
                decoded.createdAt,
                decoded.id,
                pageable
            )
        }

    private fun page(
        accountId: UUID,
        cursor: String?,
        limit: Int,
        query: (SavedCursor, PageRequest) -> List<SavedContent>
    ): SavedContentPageResponse {
        val pageSize = limit.coerceIn(1, 50)
        val fetched = query(
            cursor?.let(SavedCursor::decode) ?: SavedCursor.initial(),
            PageRequest.of(0, pageSize + 1)
        )
        val page = fetched.take(pageSize)
        val contentById = contentRepository.findAllById(page.map(SavedContent::contentId))
            .associateBy { it.id }
        val authorById = profileService.summaries(contentById.values.map { it.ownerAccountId }.toSet())
        val followingIds = socialGraphService.followingAccountIds(accountId)
        val postById = postService.getAll(
            accountId,
            contentById.values.filterIsInstance<Post>().map(Post::id)
        )
        val reelById = reelService.getAll(
            accountId,
            contentById.values.filterIsInstance<Reel>().map(Reel::id)
        )
        val items = page.mapNotNull { saved ->
            val content = contentById[saved.contentId] ?: return@mapNotNull null
            val author = authorById[content.ownerAccountId] ?: return@mapNotNull null
            ContentFeedItemResponse(
                id = content.id,
                type = when (content) {
                    is Post -> ContentKind.POST
                    is Reel -> ContentKind.REEL
                    else -> return@mapNotNull null
                },
                author = author,
                isFollowingAuthor = content.ownerAccountId in followingIds,
                canShareToStory = postById[content.id]?.canShareToStory ?: reelById[content.id]?.canShareToStory ?: false,
                saved = true,
                savedAt = saved.createdAt,
                post = postById[content.id],
                reel = reelById[content.id],
                publishedAt = requireNotNull(content.publishedAt)
            )
        }
        return SavedContentPageResponse(
            items = items,
            nextCursor = page.lastOrNull()
                ?.takeIf { fetched.size > pageSize }
                ?.let(SavedCursor::from)
                ?.encode()
        )
    }

    private fun requireActive(accountId: UUID) {
        if (!isActive(accountId)) {
            throw IdentityConflictException("Account must be active to save content")
        }
    }

    private fun isActive(accountId: UUID): Boolean =
        accountRepository.findById(accountId).orElse(null)?.status == AccountStatus.ACTIVE

    private fun contentNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found")

    private data class SavedCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$createdAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun initial() = SavedCursor(Instant.now().plusMillis(1), MAX_UUID)
            fun from(saved: SavedContent) = SavedCursor(saved.createdAt, saved.id)

            fun decode(value: String): SavedCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid saved-content cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid saved-content cursor" }
                return SavedCursor(
                    createdAt = runCatching { Instant.parse(parts[0]) }
                        .getOrElse { throw IllegalArgumentException("Invalid saved-content cursor") },
                    id = runCatching { UUID.fromString(parts[1]) }
                        .getOrElse { throw IllegalArgumentException("Invalid saved-content cursor") }
                )
            }
        }
    }

    private companion object {
        val MAX_UUID: UUID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
    }
}
