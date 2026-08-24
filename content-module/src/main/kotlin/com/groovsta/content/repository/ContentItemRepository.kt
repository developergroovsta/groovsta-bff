package com.groovsta.content.repository

import com.groovsta.content.model.ContentItem
import com.groovsta.content.model.ContentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ContentItemRepository : JpaRepository<ContentItem, UUID> {
    fun findByStatusAndOwnerAccountIdInAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
        status: ContentStatus,
        owners: Collection<UUID>,
        before: Instant,
        pageable: Pageable
    ): List<ContentItem>

    @Query(
        """
        select content from ContentItem content
        where content.status = :status
          and content.ownerAccountId in :owners
          and content.publishedAt < :before
          and (
              (type(content) = Post and content.ownerAccountId not in :mutedPostOwners)
              or (type(content) = Reel and content.ownerAccountId not in :mutedReelOwners)
          )
        order by content.publishedAt desc, content.id desc
        """
    )
    fun findPriorityContent(
        @Param("status") status: ContentStatus,
        @Param("owners") owners: Collection<UUID>,
        @Param("mutedPostOwners") mutedPostOwners: Collection<UUID>,
        @Param("mutedReelOwners") mutedReelOwners: Collection<UUID>,
        @Param("before") before: Instant,
        pageable: Pageable
    ): List<ContentItem>

    @Query(
        """
        select content from ContentItem content, Profile profile
        where profile.accountId = content.ownerAccountId
          and profile.isPublic = true
          and content.status = :status
          and content.ownerAccountId not in :excludedOwners
          and (
              (type(content) = Post and content.ownerAccountId not in :mutedPostOwners)
              or (type(content) = Reel and content.ownerAccountId not in :mutedReelOwners)
          )
          and content.publishedAt < :before
        order by content.publishedAt desc, content.id desc
        """
    )
    fun findPublicContentExcludingOwners(
        @Param("status") status: ContentStatus,
        @Param("excludedOwners") excludedOwners: Collection<UUID>,
        @Param("mutedPostOwners") mutedPostOwners: Collection<UUID>,
        @Param("mutedReelOwners") mutedReelOwners: Collection<UUID>,
        @Param("before") before: Instant,
        pageable: Pageable
    ): List<ContentItem>
}
