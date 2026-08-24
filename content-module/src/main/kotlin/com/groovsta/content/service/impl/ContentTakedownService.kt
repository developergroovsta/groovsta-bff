package com.groovsta.content.service.impl

import com.groovsta.content.model.ContentStatus
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.shared.moderation.ContentTakedownPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ContentTakedownService(
    private val contentRepository: ContentItemRepository
) : ContentTakedownPort {
    @Transactional
    override fun takeDown(contentId: UUID, reason: String?) {
        val content = contentRepository.findById(contentId).orElseThrow {
            IllegalArgumentException("Content was not found")
        }
        require(content.status == ContentStatus.PUBLISHED) { "Only published content can be taken down" }
        content.moderationReason = reason?.takeIf(String::isNotBlank)
        content.status = ContentStatus.TAKEN_DOWN
        content.updatedAt = Instant.now()
    }
}
