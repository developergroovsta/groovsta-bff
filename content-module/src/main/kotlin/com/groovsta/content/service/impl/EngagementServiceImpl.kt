package com.groovsta.content.service.impl

import com.groovsta.content.dto.CommentResponse
import com.groovsta.content.dto.CommentMentionResponse
import com.groovsta.content.dto.CommentPageResponse
import com.groovsta.content.dto.CommentLikeResponse
import com.groovsta.content.dto.CommentViewerStateResponse
import com.groovsta.content.dto.CreateCommentRequest
import com.groovsta.content.dto.LikeResponse
import com.groovsta.content.dto.StoryLikeResponse
import com.groovsta.content.model.ContentComment
import com.groovsta.content.model.ContentCommentMention
import com.groovsta.content.model.ContentLike
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.repository.ContentCommentRepository
import com.groovsta.content.repository.ContentCommentMentionRepository
import com.groovsta.content.repository.ContentCommentLikeRepository
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.repository.ContentLikeRepository
import com.groovsta.content.repository.StoryRepository
import com.groovsta.content.service.EngagementService
import com.groovsta.content.service.StoryService
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.identity.dto.response.ProfileSummaryPageResponse
import com.groovsta.identity.service.ProfileService
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import com.groovsta.shared.reward.RewardEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class EngagementServiceImpl(
    private val contentRepository: ContentItemRepository,
    private val likeRepository: ContentLikeRepository,
    private val commentRepository: ContentCommentRepository,
    private val mentionRepository: ContentCommentMentionRepository,
    private val commentLikeRepository: ContentCommentLikeRepository,
    private val storyRepository: StoryRepository,
    private val profileService: ProfileService,
    private val storyService: StoryService,
    private val notificationPublisher: NotificationPublisher,
    private val rewardEventPublisher: RewardEventPublisher
) : EngagementService {

    @Transactional
    override fun like(accountId: UUID, contentId: UUID): LikeResponse {
        val content = published(contentId)
        var createdLike: ContentLike? = null
        if (!likeRepository.existsByContentIdAndAccountId(contentId, accountId)) {
            try {
                createdLike = likeRepository.saveAndFlush(ContentLike(contentId = contentId, accountId = accountId))
            }
            catch (_: DataIntegrityViolationException) { /* idempotent concurrent like */ }
        }
        createdLike?.let {
            notificationPublisher.publish(
                NotificationCommand(
                    content.ownerAccountId,
                    accountId,
                    NotificationType.CONTENT_LIKED,
                    NotificationTargetType.CONTENT,
                    content.id,
                    it.id,
                    null
                )
            )
            rewardEventPublisher.contentLiked(content.ownerAccountId, accountId, content.id, it.id, it.createdAt)
        }
        return LikeResponse(contentId, true, likeRepository.countByContentId(contentId))
    }

    @Transactional
    override fun unlike(accountId: UUID, contentId: UUID): LikeResponse {
        published(contentId)
        likeRepository.deleteByContentIdAndAccountId(contentId, accountId)
        return LikeResponse(contentId, false, likeRepository.countByContentId(contentId))
    }

    @Transactional(readOnly = true)
    override fun likers(
        viewerAccountId: UUID?,
        contentId: UUID,
        cursor: String?,
        limit: Int
    ): ProfileSummaryPageResponse {
        published(contentId)
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val likes = cursor?.let(LikeCursor::decode)?.let {
            likeRepository.findContentLikesAfter(contentId, it.createdAt, it.id, pageable)
        } ?: likeRepository.findByContentIdOrderByCreatedAtDescIdDesc(contentId, pageable)
        return likerPage(likes, pageSize)
    }

    @Transactional
    override fun comment(accountId: UUID, contentId: UUID, request: CreateCommentRequest): CommentResponse {
        val content = published(contentId)
        val replyTarget = request.replyToCommentId?.let { replyToId ->
            commentRepository.findById(replyToId).orElseThrow { notFound("Reply target") }.also {
                if (it.contentId != contentId || it.deletedAt != null) throw notFound("Reply target")
            }
        }
        val mentionProfiles = validateMentions(request)
        val commentId = UUID.randomUUID()
        val comment = commentRepository.save(
            ContentComment(
                id = commentId,
                contentId = contentId,
                accountId = accountId,
                text = request.text,
                threadRootId = replyTarget?.threadRootId ?: commentId,
                replyToCommentId = replyTarget?.id,
                depth = if (replyTarget == null) 0 else 1
            )
        )
        val mentions = request.mentions.map { mention ->
            val profile = requireNotNull(mentionProfiles[mention.accountId])
            ContentCommentMention(
                commentId = comment.id,
                mentionedAccountId = mention.accountId,
                handle = profile.handle,
                startIndex = mention.start,
                endExclusive = mention.endExclusive
            )
        }.let(mentionRepository::saveAll)
        val author = profileService.summaries(setOf(accountId))[accountId] ?: throw notFound("Comment author")
        val primaryRecipient = replyTarget?.accountId ?: content.ownerAccountId
        val mentionedAccountIds = publishMentionNotifications(
            mentions,
            accountId,
            NotificationTargetType.CONTENT,
            content.id,
            comment
        )
        if (primaryRecipient !in mentionedAccountIds) {
            notificationPublisher.publish(
                NotificationCommand(
                    primaryRecipient,
                    accountId,
                    NotificationType.CONTENT_COMMENTED,
                    NotificationTargetType.CONTENT,
                    content.id,
                    comment.id,
                    comment.id,
                    comment.text
                )
            )
        }
        return response(comment, accountId, author, mentions, replyCount = 0, likeCount = 0, viewerLiked = false)
    }

    @Transactional(readOnly = true)
    override fun comments(viewerAccountId: UUID?, contentId: UUID, cursor: String?, limit: Int): CommentPageResponse {
        published(contentId)
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val comments = cursor?.let(CommentCursor::decode)?.let {
            commentRepository.findRootCommentsAfter(contentId, it.createdAt, it.id, pageable)
        } ?: commentRepository.findByContentIdAndDepthOrderByCreatedAtDescIdDesc(contentId, 0, pageable)
        return page(comments, viewerAccountId, pageSize)
    }

    @Transactional(readOnly = true)
    override fun replies(
        viewerAccountId: UUID?,
        contentId: UUID,
        commentId: UUID,
        cursor: String?,
        limit: Int
    ): CommentPageResponse {
        published(contentId)
        val root = commentRepository.findById(commentId).orElseThrow { notFound("Comment") }
        if (root.contentId != contentId || root.depth != 0) throw notFound("Comment")
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val replies = cursor?.let(CommentCursor::decode)?.let {
            commentRepository.findRepliesAfter(contentId, root.id, it.createdAt, it.id, pageable)
        } ?: commentRepository.findByContentIdAndThreadRootIdAndDepthOrderByCreatedAtAscIdAsc(
            contentId, root.id, 1, pageable
        )
        return page(replies, viewerAccountId, pageSize)
    }

    @Transactional
    override fun likeComment(accountId: UUID, contentId: UUID, commentId: UUID): CommentLikeResponse {
        val comment = commentForEngagement(contentId, commentId)
        val now = Instant.now()
        val likeId = UUID.randomUUID()
        if (commentLikeRepository.insertIfAbsent(likeId, commentId, accountId, now) > 0) {
            notificationPublisher.publish(
                NotificationCommand(
                    comment.accountId,
                    accountId,
                    NotificationType.COMMENT_LIKED,
                    NotificationTargetType.CONTENT,
                    contentId,
                    likeId,
                    comment.id,
                    comment.text
                )
            )
        }
        return CommentLikeResponse(commentId, true, commentLikeRepository.countByCommentId(commentId))
    }

    @Transactional
    override fun unlikeComment(accountId: UUID, contentId: UUID, commentId: UUID): CommentLikeResponse {
        commentForEngagement(contentId, commentId)
        commentLikeRepository.deleteByCommentIdAndAccountId(commentId, accountId)
        return CommentLikeResponse(commentId, false, commentLikeRepository.countByCommentId(commentId))
    }

    @Transactional
    override fun deleteComment(accountId: UUID, contentId: UUID, commentId: UUID) {
        val comment = commentRepository.findById(commentId).orElseThrow { notFound("Comment") }
        if (comment.contentId != contentId || comment.accountId != accountId) throw notFound("Comment")
        softDelete(comment)
    }

    @Transactional
    override fun likeStory(accountId: UUID, storyId: UUID): StoryLikeResponse {
        storyService.ensureVisible(accountId, storyId)
        val story = storyRepository.findById(storyId).orElseThrow { notFound("Story") }
        var createdLike: ContentLike? = null
        if (!likeRepository.existsByStoryIdAndAccountId(storyId, accountId)) {
            try {
                createdLike = likeRepository.saveAndFlush(ContentLike(storyId = storyId, accountId = accountId))
            } catch (_: DataIntegrityViolationException) {
                // Idempotent concurrent like.
            }
        }
        createdLike?.let {
            notificationPublisher.publish(
                NotificationCommand(
                    story.ownerAccountId,
                    accountId,
                    NotificationType.STORY_LIKED,
                    NotificationTargetType.STORY,
                    story.id,
                    it.id,
                    null
                )
            )
            rewardEventPublisher.storyLiked(story.ownerAccountId, accountId, story.id, it.id, it.createdAt)
        }
        return StoryLikeResponse(storyId, true, likeRepository.countByStoryId(storyId))
    }

    @Transactional
    override fun unlikeStory(accountId: UUID, storyId: UUID): StoryLikeResponse {
        storyService.ensureVisible(accountId, storyId)
        likeRepository.deleteByStoryIdAndAccountId(storyId, accountId)
        return StoryLikeResponse(storyId, false, likeRepository.countByStoryId(storyId))
    }

    @Transactional(readOnly = true)
    override fun storyLikers(
        viewerAccountId: UUID?,
        storyId: UUID,
        cursor: String?,
        limit: Int
    ): ProfileSummaryPageResponse {
        storyService.ensureVisible(viewerAccountId, storyId)
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val likes = cursor?.let(LikeCursor::decode)?.let {
            likeRepository.findStoryLikesAfter(storyId, it.createdAt, it.id, pageable)
        } ?: likeRepository.findByStoryIdOrderByCreatedAtDescIdDesc(storyId, pageable)
        return likerPage(likes, pageSize)
    }

    @Transactional
    override fun commentOnStory(
        accountId: UUID,
        storyId: UUID,
        request: CreateCommentRequest
    ): CommentResponse {
        storyService.ensureVisible(accountId, storyId)
        val story = storyRepository.findById(storyId).orElseThrow { notFound("Story") }
        val replyTarget = request.replyToCommentId?.let { replyToId ->
            commentRepository.findById(replyToId).orElseThrow { notFound("Reply target") }.also {
                if (it.storyId != storyId || it.deletedAt != null) throw notFound("Reply target")
            }
        }
        val mentionProfiles = validateMentions(request)
        val commentId = UUID.randomUUID()
        val comment = commentRepository.save(
            ContentComment(
                id = commentId,
                storyId = storyId,
                accountId = accountId,
                text = request.text,
                threadRootId = replyTarget?.threadRootId ?: commentId,
                replyToCommentId = replyTarget?.id,
                depth = if (replyTarget == null) 0 else 1
            )
        )
        val mentions = request.mentions.map { mention ->
            val profile = requireNotNull(mentionProfiles[mention.accountId])
            ContentCommentMention(
                commentId = comment.id,
                mentionedAccountId = mention.accountId,
                handle = profile.handle,
                startIndex = mention.start,
                endExclusive = mention.endExclusive
            )
        }.let(mentionRepository::saveAll)
        val author = profileService.summaries(setOf(accountId))[accountId] ?: throw notFound("Comment author")
        val primaryRecipient = replyTarget?.accountId ?: story.ownerAccountId
        val mentionedAccountIds = publishMentionNotifications(
            mentions,
            accountId,
            NotificationTargetType.STORY,
            story.id,
            comment
        )
        if (primaryRecipient !in mentionedAccountIds) {
            notificationPublisher.publish(
                NotificationCommand(
                    primaryRecipient,
                    accountId,
                    NotificationType.STORY_COMMENTED,
                    NotificationTargetType.STORY,
                    story.id,
                    comment.id,
                    comment.id,
                    comment.text
                )
            )
        }
        return response(comment, accountId, author, mentions, replyCount = 0, likeCount = 0, viewerLiked = false)
    }

    @Transactional(readOnly = true)
    override fun storyComments(
        viewerAccountId: UUID?,
        storyId: UUID,
        cursor: String?,
        limit: Int
    ): CommentPageResponse {
        storyService.ensureVisible(viewerAccountId, storyId)
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val comments = cursor?.let(CommentCursor::decode)?.let {
            commentRepository.findStoryRootCommentsAfter(storyId, it.createdAt, it.id, pageable)
        } ?: commentRepository.findByStoryIdAndDepthOrderByCreatedAtDescIdDesc(storyId, 0, pageable)
        return page(comments, viewerAccountId, pageSize)
    }

    @Transactional(readOnly = true)
    override fun storyReplies(
        viewerAccountId: UUID?,
        storyId: UUID,
        commentId: UUID,
        cursor: String?,
        limit: Int
    ): CommentPageResponse {
        storyService.ensureVisible(viewerAccountId, storyId)
        val root = commentRepository.findById(commentId).orElseThrow { notFound("Comment") }
        if (root.storyId != storyId || root.depth != 0) throw notFound("Comment")
        val pageSize = limit.coerceIn(1, 100)
        val pageable = PageRequest.of(0, pageSize + 1)
        val replies = cursor?.let(CommentCursor::decode)?.let {
            commentRepository.findStoryRepliesAfter(storyId, root.id, it.createdAt, it.id, pageable)
        } ?: commentRepository.findByStoryIdAndThreadRootIdAndDepthOrderByCreatedAtAscIdAsc(
            storyId, root.id, 1, pageable
        )
        return page(replies, viewerAccountId, pageSize)
    }

    @Transactional
    override fun likeStoryComment(accountId: UUID, storyId: UUID, commentId: UUID): CommentLikeResponse {
        val comment = storyCommentForEngagement(accountId, storyId, commentId)
        val likeId = UUID.randomUUID()
        if (commentLikeRepository.insertIfAbsent(likeId, commentId, accountId, Instant.now()) > 0) {
            notificationPublisher.publish(
                NotificationCommand(
                    comment.accountId,
                    accountId,
                    NotificationType.COMMENT_LIKED,
                    NotificationTargetType.STORY,
                    storyId,
                    likeId,
                    comment.id,
                    comment.text
                )
            )
        }
        return CommentLikeResponse(commentId, true, commentLikeRepository.countByCommentId(commentId))
    }

    @Transactional
    override fun unlikeStoryComment(accountId: UUID, storyId: UUID, commentId: UUID): CommentLikeResponse {
        storyCommentForEngagement(accountId, storyId, commentId)
        commentLikeRepository.deleteByCommentIdAndAccountId(commentId, accountId)
        return CommentLikeResponse(commentId, false, commentLikeRepository.countByCommentId(commentId))
    }

    @Transactional
    override fun deleteStoryComment(accountId: UUID, storyId: UUID, commentId: UUID) {
        storyService.ensureVisible(accountId, storyId)
        val comment = commentRepository.findById(commentId).orElseThrow { notFound("Comment") }
        if (comment.storyId != storyId || comment.accountId != accountId) throw notFound("Comment")
        softDelete(comment)
    }

    private fun published(contentId: UUID) = contentRepository.findById(contentId).orElseThrow { notFound("Content") }.also {
        if (it.status != ContentStatus.PUBLISHED) throw notFound("Content")
    }

    private fun commentForEngagement(contentId: UUID, commentId: UUID) = published(contentId).let {
        commentRepository.findById(commentId).orElseThrow { notFound("Comment") }.also { comment ->
            if (comment.contentId != contentId || comment.deletedAt != null) throw notFound("Comment")
        }
    }

    private fun storyCommentForEngagement(accountId: UUID, storyId: UUID, commentId: UUID) =
        storyService.ensureVisible(accountId, storyId).let {
            commentRepository.findById(commentId).orElseThrow { notFound("Comment") }.also { comment ->
                if (comment.storyId != storyId || comment.deletedAt != null) throw notFound("Comment")
            }
        }

    private fun softDelete(comment: ContentComment) {
        if (comment.deletedAt == null) {
            comment.text = ""
            comment.deletedAt = Instant.now()
            comment.updatedAt = comment.deletedAt!!
            mentionRepository.deleteByCommentId(comment.id)
            commentLikeRepository.deleteByCommentId(comment.id)
        }
    }

    private fun validateMentions(request: CreateCommentRequest): Map<UUID, ProfileSummaryResponse> {
        if (request.mentions.isEmpty()) return emptyMap()
        val profiles = profileService.mentionableSummaries(request.mentions.map { it.accountId }.toSet())
        CommentMentionValidator.validate(request.text, request.mentions, profiles)
        return profiles
    }

    private fun publishMentionNotifications(
        mentions: Collection<ContentCommentMention>,
        actorAccountId: UUID,
        targetType: NotificationTargetType,
        targetId: UUID,
        comment: ContentComment
    ): Set<UUID> {
        val uniqueMentions = mentions.distinctBy(ContentCommentMention::mentionedAccountId)
        uniqueMentions.forEach { mention ->
            notificationPublisher.publish(
                NotificationCommand(
                    mention.mentionedAccountId,
                    actorAccountId,
                    NotificationType.COMMENT_MENTIONED,
                    targetType,
                    targetId,
                    mention.id,
                    comment.id,
                    comment.text
                )
            )
        }
        return uniqueMentions.mapTo(mutableSetOf(), ContentCommentMention::mentionedAccountId)
    }

    private fun page(comments: List<ContentComment>, viewerAccountId: UUID?, pageSize: Int): CommentPageResponse {
        val page = comments.take(pageSize)
        val authors = profileService.summaries(page.map { it.accountId }.toSet())
        val mentions = if (page.isEmpty()) emptyMap() else mentionRepository
            .findByCommentIdInOrderByStartIndexAsc(page.map { it.id })
            .groupBy { it.commentId }
        val rootIds = page.filter { it.depth == 0 }.map { it.id }
        val replyCounts = if (rootIds.isEmpty()) emptyMap() else commentRepository
            .countRepliesByThreadRootIds(rootIds)
            .associate { it.threadRootId to it.replyCount }
        val commentIds = page.map { it.id }
        val likeCounts = if (commentIds.isEmpty()) emptyMap() else commentLikeRepository
            .countByCommentIds(commentIds)
            .associate { it.commentId to it.likeCount }
        val likedCommentIds = if (viewerAccountId == null || commentIds.isEmpty()) emptySet()
        else commentLikeRepository.findLikedCommentIds(viewerAccountId, commentIds)
        val items = page.map { comment ->
            response(
                comment = comment,
                viewerAccountId = viewerAccountId,
                author = authors[comment.accountId] ?: throw notFound("Comment author"),
                mentions = mentions[comment.id].orEmpty(),
                replyCount = replyCounts[comment.id] ?: 0,
                likeCount = if (comment.deletedAt == null) likeCounts[comment.id] ?: 0 else 0,
                viewerLiked = comment.deletedAt == null && comment.id in likedCommentIds
            )
        }
        return CommentPageResponse(
            items = items,
            nextCursor = page.lastOrNull()?.takeIf { comments.size > pageSize }?.let(CommentCursor::from)?.encode()
        )
    }

    private fun likerPage(likes: List<ContentLike>, pageSize: Int): ProfileSummaryPageResponse {
        val page = likes.take(pageSize)
        val accountIds = page.map(ContentLike::accountId)
        val profiles = profileService.summaries(accountIds.toSet())
        return ProfileSummaryPageResponse(
            items = accountIds.mapNotNull(profiles::get),
            nextCursor = page.lastOrNull()
                ?.takeIf { likes.size > pageSize }
                ?.let(LikeCursor::from)
                ?.encode()
        )
    }

    private fun response(
        comment: ContentComment,
        viewerAccountId: UUID?,
        author: ProfileSummaryResponse,
        mentions: List<ContentCommentMention>,
        replyCount: Long,
        likeCount: Long,
        viewerLiked: Boolean
    ) = CommentResponse(
        id = comment.id,
        contentId = comment.contentId,
        storyId = comment.storyId,
        threadRootId = comment.threadRootId,
        replyToCommentId = comment.replyToCommentId,
        depth = comment.depth,
        accountId = comment.accountId,
        author = author,
        text = if (comment.deletedAt == null) comment.text else "[deleted]",
        mentions = if (comment.deletedAt != null) emptyList() else mentions.map {
            CommentMentionResponse(it.mentionedAccountId, it.handle, it.startIndex, it.endExclusive)
        },
        replyCount = replyCount,
        likeCount = likeCount,
        createdAt = comment.createdAt,
        isDeleted = comment.deletedAt != null,
        viewerState = CommentViewerStateResponse(
            canDelete = comment.deletedAt == null && viewerAccountId == comment.accountId,
            canReply = comment.deletedAt == null && viewerAccountId != null,
            canLike = comment.deletedAt == null && viewerAccountId != null,
            liked = viewerLiked
        )
    )

    private data class CommentCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$createdAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(comment: ContentComment) = CommentCursor(comment.createdAt, comment.id)

            fun decode(value: String): CommentCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid comment cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid comment cursor" }
                return CommentCursor(
                    createdAt = runCatching { Instant.parse(parts[0]) }
                        .getOrElse { throw IllegalArgumentException("Invalid comment cursor") },
                    id = runCatching { UUID.fromString(parts[1]) }
                        .getOrElse { throw IllegalArgumentException("Invalid comment cursor") }
                )
            }
        }
    }

    private data class LikeCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$createdAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(like: ContentLike) = LikeCursor(like.createdAt, like.id)

            fun decode(value: String): LikeCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid like cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid like cursor" }
                return LikeCursor(
                    createdAt = runCatching { Instant.parse(parts[0]) }
                        .getOrElse { throw IllegalArgumentException("Invalid like cursor") },
                    id = runCatching { UUID.fromString(parts[1]) }
                        .getOrElse { throw IllegalArgumentException("Invalid like cursor") }
                )
            }
        }
    }

    private fun notFound(type: String) = ResponseStatusException(HttpStatus.NOT_FOUND, "$type was not found")
}
