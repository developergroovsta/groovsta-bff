package com.groovsta.content.repository

import com.groovsta.content.model.ContentLike
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ContentLikeRepository : JpaRepository<ContentLike, UUID> {
    fun countByContentId(contentId: UUID): Long
    fun existsByContentIdAndAccountId(contentId: UUID, accountId: UUID): Boolean
    fun deleteByContentIdAndAccountId(contentId: UUID, accountId: UUID): Long
    fun countByStoryId(storyId: UUID): Long
    fun existsByStoryIdAndAccountId(storyId: UUID, accountId: UUID): Boolean
    fun deleteByStoryIdAndAccountId(storyId: UUID, accountId: UUID): Long

    fun findByContentIdOrderByCreatedAtDescIdDesc(contentId: UUID, pageable: Pageable): List<ContentLike>
    fun findByStoryIdOrderByCreatedAtDescIdDesc(storyId: UUID, pageable: Pageable): List<ContentLike>

    @Query(
        """
        select contentLike from ContentLike contentLike
        where contentLike.contentId = :contentId
          and (contentLike.createdAt < :createdAt
            or (contentLike.createdAt = :createdAt and contentLike.id < :id))
        order by contentLike.createdAt desc, contentLike.id desc
        """
    )
    fun findContentLikesAfter(
        @Param("contentId") contentId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ContentLike>

    @Query(
        """
        select contentLike from ContentLike contentLike
        where contentLike.storyId = :storyId
          and (contentLike.createdAt < :createdAt
            or (contentLike.createdAt = :createdAt and contentLike.id < :id))
        order by contentLike.createdAt desc, contentLike.id desc
        """
    )
    fun findStoryLikesAfter(
        @Param("storyId") storyId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ContentLike>

    @Query(
        """
        select contentLike.storyId as storyId, count(contentLike) as likeCount
        from ContentLike contentLike
        where contentLike.storyId in :storyIds
        group by contentLike.storyId
        """
    )
    fun countByStoryIds(@Param("storyIds") storyIds: Collection<UUID>): List<StoryLikeCount>

    @Query(
        """
        select contentLike.storyId from ContentLike contentLike
        where contentLike.accountId = :accountId and contentLike.storyId in :storyIds
        """
    )
    fun findLikedStoryIds(
        @Param("accountId") accountId: UUID,
        @Param("storyIds") storyIds: Collection<UUID>
    ): Set<UUID>

    @Query(
        """
        select contentLike.contentId from ContentLike contentLike
        where contentLike.accountId = :accountId and contentLike.contentId in :contentIds
        """
    )
    fun findLikedContentIds(
        @Param("accountId") accountId: UUID,
        @Param("contentIds") contentIds: Collection<UUID>
    ): Set<UUID>
}

interface StoryLikeCount {
    val storyId: UUID
    val likeCount: Long
}
