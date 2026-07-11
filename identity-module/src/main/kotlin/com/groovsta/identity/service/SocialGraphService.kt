package com.groovsta.identity.service

import com.groovsta.identity.dto.response.FollowResponse
import java.util.UUID

interface SocialGraphService {
    fun follow(followerAccountId: UUID, targetHandle: String): FollowResponse
    fun unfollow(followerAccountId: UUID, targetHandle: String): FollowResponse
}
