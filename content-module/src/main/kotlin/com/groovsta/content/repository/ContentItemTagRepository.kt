package com.groovsta.content.repository

import com.groovsta.content.model.ContentItemTag
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentItemTagRepository : JpaRepository<ContentItemTag, UUID> {
    fun findByContentIdOrderByPositionAsc(contentId: UUID): List<ContentItemTag>
    fun findByContentIdInOrderByContentIdAscPositionAsc(contentIds: Collection<UUID>): List<ContentItemTag>
}
