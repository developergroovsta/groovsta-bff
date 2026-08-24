package com.groovsta.content.repository

import com.groovsta.content.model.ContentCommentMention
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentCommentMentionRepository : JpaRepository<ContentCommentMention, UUID> {
    fun findByCommentIdInOrderByStartIndexAsc(commentIds: Collection<UUID>): List<ContentCommentMention>
    fun deleteByCommentId(commentId: UUID)
}
