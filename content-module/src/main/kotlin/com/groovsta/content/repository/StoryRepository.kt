package com.groovsta.content.repository

import com.groovsta.content.model.Story
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.StoryStatus
import com.groovsta.content.model.StoryVisibility
import com.groovsta.identity.model.AccountStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface StoryRepository : JpaRepository<Story, UUID> {
    @Query(
        """
        select story from Story story
        where story.ownerAccountId = :ownerAccountId
          and story.status = :publishedStatus
          and story.publishedAt is not null
        order by story.publishedAt desc, story.id desc
        """
    )
    fun findArchive(
        @Param("ownerAccountId") ownerAccountId: UUID,
        @Param("publishedStatus") publishedStatus: StoryStatus,
        pageable: Pageable
    ): List<Story>

    @Query(
        """
        select story from Story story
        where story.ownerAccountId = :ownerAccountId
          and story.status = :publishedStatus
          and story.publishedAt is not null
          and (
              story.publishedAt < :publishedAt
              or (story.publishedAt = :publishedAt and story.id < :id)
          )
        order by story.publishedAt desc, story.id desc
        """
    )
    fun findArchiveAfter(
        @Param("ownerAccountId") ownerAccountId: UUID,
        @Param("publishedStatus") publishedStatus: StoryStatus,
        @Param("publishedAt") publishedAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<Story>

    @Query(
        """
        select story
        from Story story
        join Account owner on owner.id = story.ownerAccountId
        join Profile ownerProfile on ownerProfile.accountId = story.ownerAccountId
        left join StoryView storyView
          on storyView.storyId = story.id
         and storyView.viewerAccountId = :viewerAccountId
        where owner.status = :activeStatus
          and story.status = :publishedStatus
          and story.expiresAt > :now
          and story.ownerAccountId not in :mutedOwnerAccountIds
          and (
              ownerProfile.isPublic = true
              or story.ownerAccountId = :viewerAccountId
              or (
                  :viewerAccountId is not null
                  and exists (
                      select privacyConnection.id
                      from Follow privacyConnection, Account privacyViewer
                      where privacyConnection.followerAccountId = :viewerAccountId
                        and privacyConnection.followedAccountId = story.ownerAccountId
                        and privacyViewer.id = privacyConnection.followerAccountId
                        and privacyViewer.status = :activeStatus
                  )
              )
          )
          and (
              story.ownsMedia = true
              or exists (
                  select linked.id
                  from ContentItem linked, Account linkedOwner, Profile linkedOwnerProfile
                  where linked.id = story.linkedContentId
                    and linked.status = :contentPublishedStatus
                    and linkedOwner.id = linked.ownerAccountId
                    and linkedOwner.status = :activeStatus
                    and linkedOwnerProfile.accountId = linked.ownerAccountId
                    and (
                        linked.ownerAccountId = story.ownerAccountId
                        or linkedOwnerProfile.isPublic = true
                    )
              )
              or exists (
                  select source.id
                  from Story source, Account sourceOwner, Profile sourceOwnerProfile
                  where source.id = story.linkedStoryId
                    and source.status = :publishedStatus
                    and source.expiresAt > :now
                    and source.publishedObjectKey is not null
                    and sourceOwner.id = source.ownerAccountId
                    and sourceOwner.status = :activeStatus
                    and sourceOwnerProfile.accountId = source.ownerAccountId
                    and (
                        source.ownerAccountId = story.ownerAccountId
                        or (
                            sourceOwnerProfile.isPublic = true
                            and source.visibility = :everyone
                        )
                    )
                    and (
                        source.ownsMedia = true
                        or exists (
                            select sourceLinked.id
                            from ContentItem sourceLinked, Account sourceLinkedOwner, Profile sourceLinkedProfile
                            where sourceLinked.id = source.linkedContentId
                              and sourceLinked.status = :contentPublishedStatus
                              and sourceLinkedOwner.id = sourceLinked.ownerAccountId
                              and sourceLinkedOwner.status = :activeStatus
                              and sourceLinkedProfile.accountId = sourceLinked.ownerAccountId
                              and (
                                  sourceLinked.ownerAccountId = source.ownerAccountId
                                  or sourceLinkedProfile.isPublic = true
                              )
                        )
                    )
              )
          )
          and (
              (case
                  when story.ownerAccountId = :viewerAccountId or storyView.id is not null then 1
                  else 0
              end) > :cursorSeenTier
              or (
                  (case
                      when story.ownerAccountId = :viewerAccountId or storyView.id is not null then 1
                      else 0
                  end) = :cursorSeenTier
                  and (
                      story.publishedAt < :before
                      or (story.publishedAt = :before and story.id < :cursorId)
                  )
              )
          )
          and (
              story.ownerAccountId = :viewerAccountId
              or story.visibility = :everyone
              or (
                  :viewerAccountId is not null
                  and story.visibility = :followers
                  and exists (
                      select connection.id
                      from Follow connection, Account viewer
                      where connection.followerAccountId = :viewerAccountId
                        and connection.followedAccountId = story.ownerAccountId
                        and viewer.id = connection.followerAccountId
                        and viewer.status = :activeStatus
                  )
              )
              or (
                  :viewerAccountId is not null
                  and story.visibility = :closeFriends
                  and exists (
                      select membership.id
                      from CloseFriend membership, Account viewer
                      where membership.ownerAccountId = story.ownerAccountId
                        and membership.friendAccountId = :viewerAccountId
                        and viewer.id = membership.friendAccountId
                        and viewer.status = :activeStatus
                  )
              )
          )
        order by
            case
                when story.ownerAccountId = :viewerAccountId or storyView.id is not null then 1
                else 0
            end asc,
            story.publishedAt desc,
            story.id desc
        """
    )
    fun findVisibleStories(
        @Param("viewerAccountId") viewerAccountId: UUID?,
        @Param("publishedStatus") publishedStatus: StoryStatus,
        @Param("contentPublishedStatus") contentPublishedStatus: ContentStatus,
        @Param("everyone") everyone: StoryVisibility,
        @Param("followers") followers: StoryVisibility,
        @Param("closeFriends") closeFriends: StoryVisibility,
        @Param("activeStatus") activeStatus: AccountStatus,
        @Param("now") now: Instant,
        @Param("mutedOwnerAccountIds") mutedOwnerAccountIds: Collection<UUID>,
        @Param("cursorSeenTier") cursorSeenTier: Int,
        @Param("before") before: Instant,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable
    ): List<Story>

    @Query(
        """
        select story
        from Story story
        join Account owner on owner.id = story.ownerAccountId
        join Profile ownerProfile on ownerProfile.accountId = story.ownerAccountId
        left join StoryView storyView
          on storyView.storyId = story.id
         and storyView.viewerAccountId = :viewerAccountId
        where owner.status = :activeStatus
          and story.ownerAccountId = :ownerAccountId
          and story.status = :publishedStatus
          and story.expiresAt > :now
          and (
              ownerProfile.isPublic = true
              or story.ownerAccountId = :viewerAccountId
              or (
                  :viewerAccountId is not null
                  and exists (
                      select privacyConnection.id
                      from Follow privacyConnection, Account privacyViewer
                      where privacyConnection.followerAccountId = :viewerAccountId
                        and privacyConnection.followedAccountId = story.ownerAccountId
                        and privacyViewer.id = privacyConnection.followerAccountId
                        and privacyViewer.status = :activeStatus
                  )
              )
          )
          and (
              story.ownsMedia = true
              or exists (
                  select linked.id
                  from ContentItem linked, Account linkedOwner, Profile linkedOwnerProfile
                  where linked.id = story.linkedContentId
                    and linked.status = :contentPublishedStatus
                    and linkedOwner.id = linked.ownerAccountId
                    and linkedOwner.status = :activeStatus
                    and linkedOwnerProfile.accountId = linked.ownerAccountId
                    and (
                        linked.ownerAccountId = story.ownerAccountId
                        or linkedOwnerProfile.isPublic = true
                    )
              )
              or exists (
                  select source.id
                  from Story source, Account sourceOwner, Profile sourceOwnerProfile
                  where source.id = story.linkedStoryId
                    and source.status = :publishedStatus
                    and source.expiresAt > :now
                    and source.publishedObjectKey is not null
                    and sourceOwner.id = source.ownerAccountId
                    and sourceOwner.status = :activeStatus
                    and sourceOwnerProfile.accountId = source.ownerAccountId
                    and (
                        source.ownerAccountId = story.ownerAccountId
                        or (
                            sourceOwnerProfile.isPublic = true
                            and source.visibility = :everyone
                        )
                    )
                    and (
                        source.ownsMedia = true
                        or exists (
                            select sourceLinked.id
                            from ContentItem sourceLinked, Account sourceLinkedOwner, Profile sourceLinkedProfile
                            where sourceLinked.id = source.linkedContentId
                              and sourceLinked.status = :contentPublishedStatus
                              and sourceLinkedOwner.id = sourceLinked.ownerAccountId
                              and sourceLinkedOwner.status = :activeStatus
                              and sourceLinkedProfile.accountId = sourceLinked.ownerAccountId
                              and (
                                  sourceLinked.ownerAccountId = source.ownerAccountId
                                  or sourceLinkedProfile.isPublic = true
                              )
                        )
                    )
              )
          )
          and (
              (case
                  when story.ownerAccountId = :viewerAccountId or storyView.id is not null then 1
                  else 0
              end) > :cursorSeenTier
              or (
                  (case
                      when story.ownerAccountId = :viewerAccountId or storyView.id is not null then 1
                      else 0
                  end) = :cursorSeenTier
                  and (
                      story.publishedAt < :before
                      or (story.publishedAt = :before and story.id < :cursorId)
                  )
              )
          )
          and (
              story.ownerAccountId = :viewerAccountId
              or story.visibility = :everyone
              or (
                  :viewerAccountId is not null
                  and story.visibility = :followers
                  and exists (
                      select connection.id
                      from Follow connection, Account viewer
                      where connection.followerAccountId = :viewerAccountId
                        and connection.followedAccountId = story.ownerAccountId
                        and viewer.id = connection.followerAccountId
                        and viewer.status = :activeStatus
                  )
              )
              or (
                  :viewerAccountId is not null
                  and story.visibility = :closeFriends
                  and exists (
                      select membership.id
                      from CloseFriend membership, Account viewer
                      where membership.ownerAccountId = story.ownerAccountId
                        and membership.friendAccountId = :viewerAccountId
                        and viewer.id = membership.friendAccountId
                        and viewer.status = :activeStatus
                  )
              )
          )
        order by
            case
                when story.ownerAccountId = :viewerAccountId or storyView.id is not null then 1
                else 0
            end asc,
            story.publishedAt desc,
            story.id desc
        """
    )
    fun findVisibleStoriesByOwner(
        @Param("viewerAccountId") viewerAccountId: UUID?,
        @Param("ownerAccountId") ownerAccountId: UUID,
        @Param("publishedStatus") publishedStatus: StoryStatus,
        @Param("contentPublishedStatus") contentPublishedStatus: ContentStatus,
        @Param("everyone") everyone: StoryVisibility,
        @Param("followers") followers: StoryVisibility,
        @Param("closeFriends") closeFriends: StoryVisibility,
        @Param("activeStatus") activeStatus: AccountStatus,
        @Param("now") now: Instant,
        @Param("cursorSeenTier") cursorSeenTier: Int,
        @Param("before") before: Instant,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable
    ): List<Story>
}
