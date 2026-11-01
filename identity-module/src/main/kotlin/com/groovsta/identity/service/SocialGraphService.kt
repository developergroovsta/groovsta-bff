package com.groovsta.identity.service

import com.groovsta.identity.dto.response.FollowResponse
import com.groovsta.identity.dto.response.FollowRequestDecisionResponse
import com.groovsta.identity.dto.response.ProfileSummaryPageResponse
import java.util.UUID

interface SocialGraphService {
    fun follow(followerAccountId: UUID, targetHandle: String): FollowResponse
    fun unfollow(followerAccountId: UUID, targetHandle: String): FollowResponse
    fun acceptFollowRequest(accountId: UUID, requestId: UUID): FollowRequestDecisionResponse
    fun rejectFollowRequest(accountId: UUID, requestId: UUID)
    fun followers(
        targetHandle: String,
        cursor: String?,
        limit: Int,
        viewerAccountId: UUID?
    ): ProfileSummaryPageResponse

    fun following(
        targetHandle: String,
        cursor: String?,
        limit: Int,
        viewerAccountId: UUID?
    ): ProfileSummaryPageResponse
    fun followingAccountIds(followerAccountId: UUID): Set<UUID>
    fun isFollowing(followerAccountId: UUID, followedAccountId: UUID): Boolean
}
