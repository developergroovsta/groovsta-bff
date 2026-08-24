package com.groovsta.content.repository

import com.groovsta.content.model.StoryMention
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StoryMentionRepository : JpaRepository<StoryMention, UUID> {
    fun findByStoryIdIn(storyIds: Collection<UUID>): List<StoryMention>
}
