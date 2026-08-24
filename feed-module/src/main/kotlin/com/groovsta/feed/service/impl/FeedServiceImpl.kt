package com.groovsta.feed.service.impl

import com.groovsta.content.service.PublishedReelQueryService
import com.groovsta.content.service.PublishedPostQueryService
import com.groovsta.content.service.PublishedContentQueryService
import com.groovsta.content.service.StoryService
import com.groovsta.feed.dto.FeedResponse
import com.groovsta.feed.dto.HomeFeedResponse
import com.groovsta.feed.dto.PostFeedResponse
import com.groovsta.feed.service.FeedService
import com.groovsta.identity.service.SocialGraphService
import com.groovsta.identity.service.ProfileService
import com.groovsta.identity.service.ContentMuteService
import com.groovsta.identity.model.ContentMuteType
import com.groovsta.identity.exception.IdentityNotFoundException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class FeedServiceImpl(
    private val reels: PublishedReelQueryService,
    private val posts: PublishedPostQueryService,
    private val content: PublishedContentQueryService,
    private val stories: StoryService,
    private val socialGraph: SocialGraphService,
    private val contentMutes: ContentMuteService,
    private val profiles: ProfileService
) : FeedService {
    override fun reels(accountId: UUID?, before: Instant, limit: Int) = response(
        reels.latestExcludingOwners(
            accountId,
            before,
            contentMutes.mutedAccountIds(accountId, ContentMuteType.REEL),
            limit.coerceIn(1, 50)
        )
    )

    override fun home(accountId: UUID?, cursor: String?, limit: Int): HomeFeedResponse {
        val priorityOwners = accountId?.let { socialGraph.followingAccountIds(it) + it }.orEmpty()
        val page = content.homeFeed(
            accountId,
            priorityOwners,
            contentMutes.mutedAccountIds(accountId, ContentMuteType.POST),
            contentMutes.mutedAccountIds(accountId, ContentMuteType.REEL),
            cursor,
            limit.coerceIn(1, 50)
        )
        return HomeFeedResponse(page.items, page.nextCursor)
    }

    override fun stories(viewerAccountId: UUID?, cursor: String?, limit: Int) =
        stories.visible(viewerAccountId, cursor, limit)

    override fun profilePosts(
        viewerAccountId: UUID?,
        handle: String,
        before: Instant,
        limit: Int
    ): PostFeedResponse {
        val profile = visibleProfile(viewerAccountId, handle)
        return postResponse(
            posts.latestPostsByOwners(
                viewerAccountId,
                setOf(profile.accountId),
                before,
                limit.coerceIn(1, 50)
            )
        )
    }

    override fun profileReels(
        viewerAccountId: UUID?,
        handle: String,
        before: Instant,
        limit: Int
    ): FeedResponse {
        val profile = visibleProfile(viewerAccountId, handle)
        return response(
            reels.latestByOwners(
                viewerAccountId,
                setOf(profile.accountId),
                before,
                limit.coerceIn(1, 50)
            )
        )
    }

    override fun profileStories(
        viewerAccountId: UUID?,
        handle: String,
        cursor: String?,
        limit: Int
    ) = stories.visibleByHandle(viewerAccountId, handle, cursor, limit)

    private fun visibleProfile(viewerAccountId: UUID?, handle: String) = profiles.getByHandle(handle).also { profile ->
        val mayView = profile.isPublic ||
            viewerAccountId == profile.accountId ||
            (viewerAccountId != null && socialGraph.isFollowing(viewerAccountId, profile.accountId))
        if (!mayView) throw IdentityNotFoundException("Profile content was not found")
    }

    private fun response(items: List<com.groovsta.content.dto.ReelResponse>) =
        FeedResponse(items, items.lastOrNull()?.publishedAt?.toString())

    private fun postResponse(items: List<com.groovsta.content.dto.PostResponse>) =
        PostFeedResponse(items, items.lastOrNull()?.publishedAt?.toString())
}
