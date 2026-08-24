package com.groovsta.content.service

import com.groovsta.content.dto.CommentResponse
import com.groovsta.content.dto.CommentPageResponse
import com.groovsta.content.dto.CommentLikeResponse
import com.groovsta.content.dto.CreateCommentRequest
import com.groovsta.content.dto.LikeResponse
import com.groovsta.content.dto.StoryLikeResponse
import com.groovsta.identity.dto.response.ProfileSummaryPageResponse
import java.util.UUID

interface EngagementService {
    fun like(accountId: UUID, contentId: UUID): LikeResponse
    fun unlike(accountId: UUID, contentId: UUID): LikeResponse
    fun likers(viewerAccountId: UUID?, contentId: UUID, cursor: String?, limit: Int): ProfileSummaryPageResponse
    fun comment(accountId: UUID, contentId: UUID, request: CreateCommentRequest): CommentResponse
    fun comments(viewerAccountId: UUID?, contentId: UUID, cursor: String?, limit: Int): CommentPageResponse
    fun replies(viewerAccountId: UUID?, contentId: UUID, commentId: UUID, cursor: String?, limit: Int): CommentPageResponse
    fun likeComment(accountId: UUID, contentId: UUID, commentId: UUID): CommentLikeResponse
    fun unlikeComment(accountId: UUID, contentId: UUID, commentId: UUID): CommentLikeResponse
    fun deleteComment(accountId: UUID, contentId: UUID, commentId: UUID)
    fun likeStory(accountId: UUID, storyId: UUID): StoryLikeResponse
    fun unlikeStory(accountId: UUID, storyId: UUID): StoryLikeResponse
    fun storyLikers(viewerAccountId: UUID?, storyId: UUID, cursor: String?, limit: Int): ProfileSummaryPageResponse
    fun commentOnStory(accountId: UUID, storyId: UUID, request: CreateCommentRequest): CommentResponse
    fun storyComments(viewerAccountId: UUID?, storyId: UUID, cursor: String?, limit: Int): CommentPageResponse
    fun storyReplies(viewerAccountId: UUID?, storyId: UUID, commentId: UUID, cursor: String?, limit: Int): CommentPageResponse
    fun likeStoryComment(accountId: UUID, storyId: UUID, commentId: UUID): CommentLikeResponse
    fun unlikeStoryComment(accountId: UUID, storyId: UUID, commentId: UUID): CommentLikeResponse
    fun deleteStoryComment(accountId: UUID, storyId: UUID, commentId: UUID)
}
