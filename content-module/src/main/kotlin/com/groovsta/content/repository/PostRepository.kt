package com.groovsta.content.repository

import com.groovsta.content.model.Post
import com.groovsta.content.model.ContentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface PostRepository : JpaRepository<Post, UUID> {
    fun countByOwnerAccountIdAndStatus(ownerAccountId: UUID, status: ContentStatus): Long

    fun findByStatusAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
        status: ContentStatus,
        before: Instant,
        pageable: Pageable
    ): List<Post>

    fun findByStatusAndOwnerAccountIdInAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
        status: ContentStatus,
        owners: Collection<UUID>,
        before: Instant,
        pageable: Pageable
    ): List<Post>
}
