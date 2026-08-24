package com.groovsta.content.repository

import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.SavedContent
import com.groovsta.identity.model.AccountStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SavedContentRepository : JpaRepository<SavedContent, UUID> {
    fun findByAccountIdAndContentId(accountId: UUID, contentId: UUID): SavedContent?
    fun findByAccountIdAndContentIdIn(accountId: UUID, contentIds: Collection<UUID>): List<SavedContent>
    fun deleteByAccountIdAndContentId(accountId: UUID, contentId: UUID): Long

    @Query(
        """
        select saved
        from SavedContent saved, ContentItem content, Profile ownerProfile, Account owner
        where saved.accountId = :viewerAccountId
          and content.id = saved.contentId
          and type(content) = Post
          and content.status = :publishedStatus
          and owner.id = content.ownerAccountId
          and owner.status = :activeStatus
          and ownerProfile.accountId = content.ownerAccountId
          and (
              content.ownerAccountId = :viewerAccountId
              or ownerProfile.isPublic = true
              or exists (
                  select connection.id
                  from Follow connection, Account follower
                  where connection.followerAccountId = :viewerAccountId
                    and connection.followedAccountId = content.ownerAccountId
                    and follower.id = connection.followerAccountId
                    and follower.status = :activeStatus
              )
          )
          and (
              saved.createdAt < :before
              or (saved.createdAt = :before and saved.id < :cursorId)
          )
        order by saved.createdAt desc, saved.id desc
        """
    )
    fun findVisibleSavedPosts(
        @Param("viewerAccountId") viewerAccountId: UUID,
        @Param("publishedStatus") publishedStatus: ContentStatus,
        @Param("activeStatus") activeStatus: AccountStatus,
        @Param("before") before: Instant,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable
    ): List<SavedContent>

    @Query(
        """
        select saved
        from SavedContent saved, ContentItem content, Profile ownerProfile, Account owner
        where saved.accountId = :viewerAccountId
          and content.id = saved.contentId
          and type(content) = Reel
          and content.status = :publishedStatus
          and owner.id = content.ownerAccountId
          and owner.status = :activeStatus
          and ownerProfile.accountId = content.ownerAccountId
          and (
              content.ownerAccountId = :viewerAccountId
              or ownerProfile.isPublic = true
              or exists (
                  select connection.id
                  from Follow connection, Account follower
                  where connection.followerAccountId = :viewerAccountId
                    and connection.followedAccountId = content.ownerAccountId
                    and follower.id = connection.followerAccountId
                    and follower.status = :activeStatus
              )
          )
          and (
              saved.createdAt < :before
              or (saved.createdAt = :before and saved.id < :cursorId)
          )
        order by saved.createdAt desc, saved.id desc
        """
    )
    fun findVisibleSavedReels(
        @Param("viewerAccountId") viewerAccountId: UUID,
        @Param("publishedStatus") publishedStatus: ContentStatus,
        @Param("activeStatus") activeStatus: AccountStatus,
        @Param("before") before: Instant,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable
    ): List<SavedContent>
}
