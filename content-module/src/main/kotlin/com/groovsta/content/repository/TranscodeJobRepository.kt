package com.groovsta.content.repository

import com.groovsta.content.model.TranscodeJob
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TranscodeJobRepository : JpaRepository<TranscodeJob, UUID> {
    fun findByStatusOrderByCreatedAt(status: String, pageable: Pageable): List<TranscodeJob>
    fun findByReelId(reelId: UUID): TranscodeJob?
    fun findByStoryId(storyId: UUID): TranscodeJob?
    fun findByChatAttachmentId(chatAttachmentId: UUID): TranscodeJob?
}
