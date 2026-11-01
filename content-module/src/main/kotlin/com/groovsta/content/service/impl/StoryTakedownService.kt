package com.groovsta.content.service.impl

import com.groovsta.content.model.StoryStatus
import com.groovsta.content.repository.StoryRepository
import com.groovsta.shared.moderation.StoryTakedownPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class StoryTakedownService(
    private val storyRepository: StoryRepository
) : StoryTakedownPort {
    @Transactional
    override fun takeDownStory(storyId: UUID, reason: String) {
        val story = storyRepository.findById(storyId).orElseThrow {
            IllegalArgumentException("Story was not found")
        }
        require(story.status == StoryStatus.PUBLISHED) { "Only published stories can be taken down" }
        story.status = StoryStatus.TAKEN_DOWN
        story.moderationReason = reason.takeIf(String::isNotBlank)
        story.updatedAt = Instant.now()
    }
}
