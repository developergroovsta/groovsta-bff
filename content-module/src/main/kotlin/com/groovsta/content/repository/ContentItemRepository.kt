package com.groovsta.content.repository

import com.groovsta.content.model.ContentItem
import com.groovsta.content.model.ContentStatus
import com.groovsta.identity.model.AccountStatus
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

    @Query(
        """
        select content
        from ContentItem content
        join Account owner on owner.id = content.ownerAccountId
        join Profile profile on profile.accountId = content.ownerAccountId
        where content.locationId = :locationId
          and content.status = :status
          and content.publishedAt < :before
          and owner.status = :activeStatus
          and (
              :contentKind is null
              or (:contentKind = 'POST' and type(content) = Post)
              or (:contentKind = 'REEL' and type(content) = Reel)
          )
          and (
              profile.isPublic = true
              or content.ownerAccountId = :viewerAccountId
              or (
                  :viewerAccountId is not null
                  and exists (
                      select connection.id
                      from Follow connection, Account viewer
                      where connection.followerAccountId = :viewerAccountId
                        and connection.followedAccountId = content.ownerAccountId
                        and viewer.id = connection.followerAccountId
                        and viewer.status = :activeStatus
                  )
              )
          )
        order by content.publishedAt desc, content.id desc
        """
    )
    fun findVisibleByLocation(
        @Param("locationId") locationId: UUID,
        @Param("viewerAccountId") viewerAccountId: UUID?,
        @Param("contentKind") contentKind: String?,
        @Param("status") status: ContentStatus,
        @Param("activeStatus") activeStatus: AccountStatus,
        @Param("before") before: Instant,
        pageable: Pageable
    ): List<ContentItem>
}
