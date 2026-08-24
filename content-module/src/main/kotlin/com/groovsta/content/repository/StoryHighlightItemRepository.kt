package com.groovsta.content.repository

import com.groovsta.content.model.StoryHighlightItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StoryHighlightItemRepository : JpaRepository<StoryHighlightItem, UUID> {
    fun existsByStoryId(storyId: UUID): Boolean
    fun findByHighlightIdOrderByPositionAsc(highlightId: UUID): List<StoryHighlightItem>
    fun findByHighlightIdInOrderByHighlightIdAscPositionAsc(
        highlightIds: Collection<UUID>
    ): List<StoryHighlightItem>
    @Modifying
    @Query("delete from StoryHighlightItem item where item.highlightId = :highlightId")
    fun deleteByHighlightId(@Param("highlightId") highlightId: UUID)
}
