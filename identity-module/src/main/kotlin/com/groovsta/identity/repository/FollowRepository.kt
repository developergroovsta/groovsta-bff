package com.groovsta.identity.repository

import com.groovsta.identity.model.Follow
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.FollowStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface FollowRelationshipEdge {
    val followerAccountId: UUID
    val followedAccountId: UUID
    val followStatus: FollowStatus
}

interface FollowRepository : JpaRepository<Follow, UUID> {
    fun findByFollowerAccountIdAndFollowedAccountId(followerAccountId: UUID, followedAccountId: UUID): Follow?
    fun deleteByFollowerAccountIdAndFollowedAccountId(followerAccountId: UUID, followedAccountId: UUID)
    fun findByIdAndFollowedAccountIdAndStatus(id: UUID, followedAccountId: UUID, status: FollowStatus): Follow?

    @Query(
        """
        select connection.followerAccountId as followerAccountId,
               connection.followedAccountId as followedAccountId,
               connection.status as followStatus
        from Follow connection, Account follower, Account followed
        where follower.id = connection.followerAccountId
          and followed.id = connection.followedAccountId
          and follower.status = :status
          and followed.status = :status
          and (
              (connection.followerAccountId = :viewerAccountId
                  and connection.followedAccountId in :targetAccountIds)
              or
              (connection.followedAccountId = :viewerAccountId
                  and connection.followerAccountId in :targetAccountIds)
          )
        """
    )
    fun findActiveRelationshipEdges(
        @Param("viewerAccountId") viewerAccountId: UUID,
        @Param("targetAccountIds") targetAccountIds: Collection<UUID>,
        @Param("status") status: AccountStatus
    ): List<FollowRelationshipEdge>

    @Query(
        """
        select case when count(connection) > 0 then true else false end
        from Follow connection, Account follower, Account followed
        where connection.followerAccountId = :followerAccountId
          and connection.followedAccountId = :followedAccountId
          and follower.id = connection.followerAccountId
          and followed.id = connection.followedAccountId
          and follower.status = :status
          and followed.status = :status
          and connection.status = :followStatus
        """
    )
    fun existsActiveFollow(
        @Param("followerAccountId") followerAccountId: UUID,
        @Param("followedAccountId") followedAccountId: UUID,
        @Param("status") status: AccountStatus,
        @Param("followStatus") followStatus: FollowStatus
    ): Boolean

    @Query(
        """
        select connection.followedAccountId
        from Follow connection, Account follower, Account followed
        where connection.followerAccountId = :followerAccountId
          and follower.id = connection.followerAccountId
          and followed.id = connection.followedAccountId
          and follower.status = :status
          and followed.status = :status
          and connection.status = :followStatus
        """
    )
    fun findActiveFollowingAccountIds(
        @Param("followerAccountId") followerAccountId: UUID,
        @Param("status") status: AccountStatus,
        @Param("followStatus") followStatus: FollowStatus
    ): List<UUID>

    @Query(
        """
        select count(connection) from Follow connection, Account account
        where connection.followedAccountId = :accountId
          and account.id = connection.followerAccountId
          and account.status = :status
          and connection.status = :followStatus
        """
    )
    fun countActiveFollowers(
        @Param("accountId") accountId: UUID,
        @Param("status") status: AccountStatus,
        @Param("followStatus") followStatus: FollowStatus
    ): Long

    @Query(
        """
        select count(connection) from Follow connection, Account account
        where connection.followerAccountId = :accountId
          and account.id = connection.followedAccountId
          and account.status = :status
          and connection.status = :followStatus
        """
    )
    fun countActiveFollowing(
        @Param("accountId") accountId: UUID,
        @Param("status") status: AccountStatus,
        @Param("followStatus") followStatus: FollowStatus
    ): Long
    fun findAllByFollowerAccountId(followerAccountId: UUID): List<Follow>

    @Query(
        """
        select connection from Follow connection, Account account
        where connection.followedAccountId = :accountId
          and account.id = connection.followerAccountId
          and account.status = :status
          and connection.status = :followStatus
        order by connection.createdAt desc, connection.id desc
        """
    )
    fun findFollowers(
        @Param("accountId") accountId: UUID,
        @Param("status") status: AccountStatus,
        @Param("followStatus") followStatus: FollowStatus,
        pageable: Pageable
    ): List<Follow>

    @Query(
        """
        select connection from Follow connection, Account account
        where connection.followedAccountId = :accountId
          and account.id = connection.followerAccountId
          and account.status = :status
          and connection.status = :followStatus
          and (connection.createdAt < :createdAt or (connection.createdAt = :createdAt and connection.id < :id))
        order by connection.createdAt desc, connection.id desc
        """
    )
    fun findFollowersAfter(
        @Param("accountId") accountId: UUID,
        @Param("status") status: AccountStatus,
        @Param("followStatus") followStatus: FollowStatus,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<Follow>

    @Query(
        """
        select connection from Follow connection, Account account
        where connection.followerAccountId = :accountId
          and account.id = connection.followedAccountId
          and account.status = :status
          and connection.status = :followStatus
        order by connection.createdAt desc, connection.id desc
        """
    )
    fun findFollowing(
        @Param("accountId") accountId: UUID,
        @Param("status") status: AccountStatus,
        @Param("followStatus") followStatus: FollowStatus,
        pageable: Pageable
    ): List<Follow>

    @Query(
        """
        select connection from Follow connection, Account account
        where connection.followerAccountId = :accountId
          and account.id = connection.followedAccountId
          and account.status = :status
          and connection.status = :followStatus
          and (connection.createdAt < :createdAt or (connection.createdAt = :createdAt and connection.id < :id))
        order by connection.createdAt desc, connection.id desc
        """
    )
    fun findFollowingAfter(
        @Param("accountId") accountId: UUID,
        @Param("status") status: AccountStatus,
        @Param("followStatus") followStatus: FollowStatus,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<Follow>
}
