package com.groovsta.content.repository

import com.groovsta.content.model.StoryHighlight
import com.groovsta.content.model.StoryStatus
import com.groovsta.content.model.StoryVisibility
import com.groovsta.content.model.ContentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface StoryHighlightRepository : JpaRepository<StoryHighlight, UUID> {
    fun findByOwnerAccountIdOrderByCreatedAtDescIdDesc(
        ownerAccountId: UUID,
        pageable: Pageable
    ): List<StoryHighlight>

    @Query(
        """
        select highlight from StoryHighlight highlight
        where highlight.ownerAccountId = :ownerAccountId
          and (
              highlight.createdAt < :createdAt
              or (highlight.createdAt = :createdAt and highlight.id < :id)
          )
        order by highlight.createdAt desc, highlight.id desc
        """
    )
    fun findByOwnerAfter(
        @Param("ownerAccountId") ownerAccountId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<StoryHighlight>

    @Query(
        """
        select highlight from StoryHighlight highlight
        where highlight.ownerAccountId = :ownerAccountId
          and exists (
              select item.id
              from StoryHighlightItem item, Story story
              where item.highlightId = highlight.id
                and story.id = item.storyId
                and story.status = :publishedStatus
                and story.visibility in :visibilities
                and (
                    story.ownsMedia = true
                    or exists (
                        select linked.id from ContentItem linked
                        where linked.id = story.linkedContentId
                          and linked.status = :contentPublishedStatus
                    )
                )
          )
        order by highlight.createdAt desc, highlight.id desc
        """
    )
    fun findVisibleByOwner(
        @Param("ownerAccountId") ownerAccountId: UUID,
        @Param("publishedStatus") publishedStatus: StoryStatus,
        @Param("contentPublishedStatus") contentPublishedStatus: ContentStatus,
        @Param("visibilities") visibilities: Collection<StoryVisibility>,
        pageable: Pageable
    ): List<StoryHighlight>

    @Query(
        """
        select highlight from StoryHighlight highlight
        where highlight.ownerAccountId = :ownerAccountId
          and (
              highlight.createdAt < :createdAt
              or (highlight.createdAt = :createdAt and highlight.id < :id)
          )
          and exists (
              select item.id
              from StoryHighlightItem item, Story story
              where item.highlightId = highlight.id
                and story.id = item.storyId
                and story.status = :publishedStatus
                and story.visibility in :visibilities
                and (
                    story.ownsMedia = true
                    or exists (
                        select linked.id from ContentItem linked
                        where linked.id = story.linkedContentId
                          and linked.status = :contentPublishedStatus
                    )
                )
          )
        order by highlight.createdAt desc, highlight.id desc
        """
    )
    fun findVisibleByOwnerAfter(
        @Param("ownerAccountId") ownerAccountId: UUID,
        @Param("publishedStatus") publishedStatus: StoryStatus,
        @Param("contentPublishedStatus") contentPublishedStatus: ContentStatus,
        @Param("visibilities") visibilities: Collection<StoryVisibility>,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<StoryHighlight>
}
