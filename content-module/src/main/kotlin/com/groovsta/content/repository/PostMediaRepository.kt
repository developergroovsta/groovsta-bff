package com.groovsta.content.repository

import com.groovsta.content.model.PostMedia
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PostMediaRepository : JpaRepository<PostMedia, UUID> {
    fun findByPostIdOrderByPositionAsc(postId: UUID): List<PostMedia>
}
