package com.groovsta.content.repository

import com.groovsta.content.model.ContentCommentLike
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ContentCommentLikeRepository : JpaRepository<ContentCommentLike, UUID> {
    @Modifying
    @Query(
        value = """
        INSERT INTO content_comment_likes (id, comment_id, account_id, created_at, updated_at)
        VALUES (:id, :commentId, :accountId, :now, :now)
        ON CONFLICT (comment_id, account_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("commentId") commentId: UUID,
        @Param("accountId") accountId: UUID,
        @Param("now") now: Instant
    ): Int

    fun countByCommentId(commentId: UUID): Long
    fun deleteByCommentIdAndAccountId(commentId: UUID, accountId: UUID): Long
    fun deleteByCommentId(commentId: UUID): Long

    @Query(
        """
        select commentLike.commentId as commentId, count(commentLike) as likeCount
        from ContentCommentLike commentLike
        where commentLike.commentId in :commentIds
        group by commentLike.commentId
        """
    )
    fun countByCommentIds(@Param("commentIds") commentIds: Collection<UUID>): List<CommentLikeCount>

    @Query(
        """
        select commentLike.commentId from ContentCommentLike commentLike
        where commentLike.accountId = :accountId and commentLike.commentId in :commentIds
        """
    )
    fun findLikedCommentIds(
        @Param("accountId") accountId: UUID,
        @Param("commentIds") commentIds: Collection<UUID>
    ): Set<UUID>
}

interface CommentLikeCount {
    val commentId: UUID
    val likeCount: Long
}
