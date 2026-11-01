package com.groovsta.feed.service

import com.groovsta.feed.dto.FeedResponse
import com.groovsta.feed.dto.HomeFeedResponse
import com.groovsta.feed.dto.PostFeedResponse
import com.groovsta.content.dto.StoryPageResponse
import java.time.Instant
import java.util.UUID

interface FeedService {
    fun reels(accountId: UUID?, before: Instant, limit: Int): FeedResponse
    fun home(accountId: UUID?, cursor: String?, limit: Int): HomeFeedResponse
    fun stories(viewerAccountId: UUID?, cursor: String?, limit: Int): StoryPageResponse
    fun profilePosts(viewerAccountId: UUID?, handle: String, before: Instant, limit: Int): PostFeedResponse
    fun profileReels(viewerAccountId: UUID?, handle: String, before: Instant, limit: Int): FeedResponse
    fun profileStories(viewerAccountId: UUID?, handle: String, cursor: String?, limit: Int): StoryPageResponse
}
