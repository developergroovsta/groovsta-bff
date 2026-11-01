package com.groovsta.content.repository

import com.groovsta.content.model.StoryView
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface StoryViewRepository : JpaRepository<StoryView, UUID> {
    fun findByStoryIdAndViewerAccountId(storyId: UUID, viewerAccountId: UUID): StoryView?
    fun findByStoryIdInAndViewerAccountId(storyIds: Collection<UUID>, viewerAccountId: UUID): List<StoryView>
    fun findByStoryIdOrderByViewedAtDescIdDesc(storyId: UUID, pageable: Pageable): List<StoryView>

    @Query(
        """
        select storyView from StoryView storyView
        where storyView.storyId = :storyId
          and (storyView.viewedAt < :viewedAt
            or (storyView.viewedAt = :viewedAt and storyView.id < :id))
        order by storyView.viewedAt desc, storyView.id desc
        """
    )
    fun findViewsAfter(
        @Param("storyId") storyId: UUID,
        @Param("viewedAt") viewedAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<StoryView>
}
