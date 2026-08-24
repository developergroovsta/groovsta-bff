package com.groovsta.messaging.service

import com.groovsta.content.model.ContentKind
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.Post
import com.groovsta.content.model.Reel
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.service.PostService
import com.groovsta.content.service.ReelService
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.ProfileService
import com.groovsta.identity.service.SocialGraphService
import com.groovsta.messaging.dto.SharedContentPreviewResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ChatContentPreviewService(
    private val contentRepository: ContentItemRepository,
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val profileService: ProfileService,
    private val socialGraphService: SocialGraphService,
    private val postService: PostService,
    private val reelService: ReelService
) {

    @Transactional(readOnly = true)
    fun requireShareableToBoth(contentId: UUID, senderAccountId: UUID, recipientAccountId: UUID) {
        val content = contentRepository.findById(contentId).orElseThrow { contentNotFound() }
        if (content.status != ContentStatus.PUBLISHED || content !is Post && content !is Reel) contentNotFound()
        if (!isActive(content.ownerAccountId)) contentNotFound()
        val ownerProfile = profileRepository.findByAccountId(content.ownerAccountId).orElseThrow { contentNotFound() }
        if (!mayView(senderAccountId, content.ownerAccountId, ownerProfile.isPublic)) contentNotFound()
        if (!mayView(recipientAccountId, content.ownerAccountId, ownerProfile.isPublic)) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "The recipient cannot view the shared content"
            )
        }
    }

    @Transactional(readOnly = true)
    fun previews(viewerAccountId: UUID, contentIds: Set<UUID>): Map<UUID, SharedContentPreviewResponse> {
        if (contentIds.isEmpty()) return emptyMap()

        val contentById = contentRepository.findAllById(contentIds).associateBy { it.id }
        val ownerIds = contentById.values.mapTo(mutableSetOf()) { it.ownerAccountId }
        val profilesByOwner = profileRepository.findByAccountIdIn(ownerIds).associateBy { it.accountId }
        val activeOwnerIds = accountRepository.findAllById(ownerIds)
            .asSequence()
            .filter { it.status == AccountStatus.ACTIVE }
            .mapTo(mutableSetOf()) { it.id }
        val followingIds = socialGraphService.followingAccountIds(viewerAccountId)

        val visibleContent = contentById.values.filter { content ->
            val profile = profilesByOwner[content.ownerAccountId]
            content.status == ContentStatus.PUBLISHED &&
                content.ownerAccountId in activeOwnerIds &&
                profile != null &&
                (content.ownerAccountId == viewerAccountId ||
                    profile.isPublic ||
                    content.ownerAccountId in followingIds)
        }
        val postById = postService.getAll(
            viewerAccountId,
            visibleContent.filterIsInstance<Post>().map(Post::id)
        )
        val reelById = reelService.getAll(
            viewerAccountId,
            visibleContent.filterIsInstance<Reel>().map(Reel::id)
        )
        val authors = profileService.summaries(visibleContent.mapTo(mutableSetOf()) { it.ownerAccountId })

        return contentIds.associateWith { contentId ->
            val content = contentById[contentId]
            val author = content?.let { authors[it.ownerAccountId] }
            when (content) {
                is Post -> {
                    val post = postById[content.id]
                    if (post == null || author == null) unavailable(contentId)
                    else SharedContentPreviewResponse(
                        contentId = content.id,
                        available = true,
                        type = ContentKind.POST,
                        author = author,
                        caption = post.caption,
                        previewUrl = post.media.firstOrNull()?.imageUrl,
                        liked = post.liked,
                        mediaCount = post.media.size,
                        durationSeconds = null
                    )
                }
                is Reel -> {
                    val reel = reelById[content.id]
                    if (reel == null || author == null) unavailable(contentId)
                    else SharedContentPreviewResponse(
                        contentId = content.id,
                        available = true,
                        type = ContentKind.REEL,
                        author = author,
                        caption = reel.caption,
                        previewUrl = reel.thumbnailUrl ?: reel.playbackUrl,
                        liked = reel.liked,
                        mediaCount = null,
                        durationSeconds = reel.durationSeconds
                    )
                }
                else -> unavailable(contentId)
            }
        }
    }

    private fun mayView(viewerAccountId: UUID, ownerAccountId: UUID, ownerIsPublic: Boolean): Boolean =
        viewerAccountId == ownerAccountId ||
            ownerIsPublic ||
            socialGraphService.isFollowing(viewerAccountId, ownerAccountId)

    private fun isActive(accountId: UUID): Boolean =
        accountRepository.findById(accountId).orElse(null)?.status == AccountStatus.ACTIVE

    private fun unavailable(contentId: UUID) = SharedContentPreviewResponse(
        contentId = contentId,
        available = false,
        type = null,
        author = null,
        caption = null,
        previewUrl = null,
        liked = false,
        mediaCount = null,
        durationSeconds = null
    )

    private fun contentNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found")
}
