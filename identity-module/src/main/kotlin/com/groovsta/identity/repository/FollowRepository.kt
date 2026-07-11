package com.groovsta.identity.repository

import com.groovsta.identity.model.Follow
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FollowRepository : JpaRepository<Follow, UUID> {
    fun existsByFollowerAccountIdAndFollowedAccountId(followerAccountId: UUID, followedAccountId: UUID): Boolean
    fun deleteByFollowerAccountIdAndFollowedAccountId(followerAccountId: UUID, followedAccountId: UUID)
    fun countByFollowedAccountId(followedAccountId: UUID): Long
    fun countByFollowerAccountId(followerAccountId: UUID): Long
}
