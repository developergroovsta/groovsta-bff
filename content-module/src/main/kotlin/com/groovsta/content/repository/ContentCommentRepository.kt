package com.groovsta.content.repository

import com.groovsta.content.model.ContentComment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ContentCommentRepository : JpaRepository<ContentComment, UUID> {
    @Query("select count(comment) from ContentComment comment where comment.contentId = :contentId and comment.deletedAt is null")
    fun countByContentId(contentId: UUID): Long
    @Query("select count(comment) from ContentComment comment where comment.storyId = :storyId and comment.deletedAt is null")
    fun countByStoryId(storyId: UUID): Long
    fun findByContentIdAndDepthOrderByCreatedAtDescIdDesc(contentId: UUID, depth: Int, pageable: Pageable): List<ContentComment>
    fun findByContentIdAndThreadRootIdAndDepthOrderByCreatedAtAscIdAsc(
        contentId: UUID,
        threadRootId: UUID,
        depth: Int,
        pageable: Pageable
    ): List<ContentComment>
    fun findByStoryIdAndDepthOrderByCreatedAtDescIdDesc(storyId: UUID, depth: Int, pageable: Pageable): List<ContentComment>
    fun findByStoryIdAndThreadRootIdAndDepthOrderByCreatedAtAscIdAsc(
        storyId: UUID,
        threadRootId: UUID,
        depth: Int,
        pageable: Pageable
    ): List<ContentComment>

    @Query(
        """
        select comment from ContentComment comment
        where comment.contentId = :contentId
          and comment.depth = 0
          and (comment.createdAt < :createdAt or (comment.createdAt = :createdAt and comment.id < :id))
        order by comment.createdAt desc, comment.id desc
        """
    )
    fun findRootCommentsAfter(
        @Param("contentId") contentId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ContentComment>

    @Query(
        """
        select comment from ContentComment comment
        where comment.contentId = :contentId
          and comment.threadRootId = :threadRootId
          and comment.depth = 1
          and (comment.createdAt > :createdAt or (comment.createdAt = :createdAt and comment.id > :id))
        order by comment.createdAt asc, comment.id asc
        """
    )
    fun findRepliesAfter(
        @Param("contentId") contentId: UUID,
        @Param("threadRootId") threadRootId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ContentComment>

    @Query(
        """
        select comment from ContentComment comment
        where comment.storyId = :storyId
          and comment.depth = 0
          and (comment.createdAt < :createdAt or (comment.createdAt = :createdAt and comment.id < :id))
        order by comment.createdAt desc, comment.id desc
        """
    )
    fun findStoryRootCommentsAfter(
        @Param("storyId") storyId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ContentComment>

    @Query(
        """
        select comment from ContentComment comment
        where comment.storyId = :storyId
          and comment.threadRootId = :threadRootId
          and comment.depth = 1
          and (comment.createdAt > :createdAt or (comment.createdAt = :createdAt and comment.id > :id))
        order by comment.createdAt asc, comment.id asc
        """
    )
    fun findStoryRepliesAfter(
        @Param("storyId") storyId: UUID,
        @Param("threadRootId") threadRootId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ContentComment>

    @Query(
        """
        select comment.storyId as storyId, count(comment) as commentCount
        from ContentComment comment
        where comment.storyId in :storyIds and comment.deletedAt is null
        group by comment.storyId
        """
    )
    fun countByStoryIds(@Param("storyIds") storyIds: Collection<UUID>): List<StoryCommentCount>

    @Query(
        """
        select comment.threadRootId as threadRootId, count(comment) as replyCount
        from ContentComment comment
        where comment.threadRootId in :threadRootIds and comment.depth = 1
        group by comment.threadRootId
        """
    )
    fun countRepliesByThreadRootIds(
        @Param("threadRootIds") threadRootIds: Collection<UUID>
    ): List<ThreadReplyCount>
}

interface ThreadReplyCount {
    val threadRootId: UUID
    val replyCount: Long
}

interface StoryCommentCount {
    val storyId: UUID
    val commentCount: Long
}
