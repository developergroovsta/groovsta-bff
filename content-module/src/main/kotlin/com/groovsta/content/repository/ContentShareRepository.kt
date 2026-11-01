package com.groovsta.content.repository

import com.groovsta.content.model.ContentShare
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ContentShareRepository : JpaRepository<ContentShare, UUID> {
    fun countByContentId(contentId: UUID): Long
    fun countByStoryId(storyId: UUID): Long
    fun existsByContentIdAndAccountId(contentId: UUID, accountId: UUID): Boolean
    fun existsByStoryIdAndAccountId(storyId: UUID, accountId: UUID): Boolean

    fun findByContentIdOrderByCreatedAtDescIdDesc(contentId: UUID, pageable: Pageable): List<ContentShare>
    fun findByStoryIdOrderByCreatedAtDescIdDesc(storyId: UUID, pageable: Pageable): List<ContentShare>

    @Query(
        """
        select share from ContentShare share
        where share.contentId = :contentId
          and (share.createdAt < :createdAt
            or (share.createdAt = :createdAt and share.id < :id))
        order by share.createdAt desc, share.id desc
        """
    )
    fun findContentSharesAfter(
        @Param("contentId") contentId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ContentShare>

    @Query(
        """
        select share from ContentShare share
        where share.storyId = :storyId
          and (share.createdAt < :createdAt
            or (share.createdAt = :createdAt and share.id < :id))
        order by share.createdAt desc, share.id desc
        """
    )
    fun findStorySharesAfter(
        @Param("storyId") storyId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ContentShare>

    @Query(
        """
        select share.contentId as contentId, count(share) as shareCount
        from ContentShare share
        where share.contentId in :contentIds
        group by share.contentId
        """
    )
    fun countByContentIds(@Param("contentIds") contentIds: Collection<UUID>): List<ContentShareCount>

    @Query(
        """
        select share.storyId as storyId, count(share) as shareCount
        from ContentShare share
        where share.storyId in :storyIds
        group by share.storyId
        """
    )
    fun countByStoryIds(@Param("storyIds") storyIds: Collection<UUID>): List<StoryShareCount>
}

interface ContentShareCount {
    val contentId: UUID
    val shareCount: Long
}

interface StoryShareCount {
    val storyId: UUID
    val shareCount: Long
}
