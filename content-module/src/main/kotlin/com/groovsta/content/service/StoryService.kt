package com.groovsta.content.service

import com.groovsta.content.dto.CompleteStoryUploadRequest
import com.groovsta.content.dto.CreateStoryRequest
import com.groovsta.content.dto.StoryCreateResponse
import com.groovsta.content.dto.StoryHighlightPageResponse
import com.groovsta.content.dto.StoryHighlightResponse
import com.groovsta.content.dto.StoryPageResponse
import com.groovsta.content.dto.StoryResponse
import com.groovsta.content.dto.StorySeenResponse
import com.groovsta.content.dto.UpsertStoryHighlightRequest
import com.groovsta.identity.dto.response.ProfileSummaryPageResponse
import java.util.UUID

interface StoryService {
    fun create(accountId: UUID, request: CreateStoryRequest): StoryCreateResponse
    fun completeUpload(accountId: UUID, storyId: UUID, request: CompleteStoryUploadRequest): StoryResponse
    fun get(viewerAccountId: UUID?, storyId: UUID): StoryResponse
    fun visible(viewerAccountId: UUID?, cursor: String?, limit: Int): StoryPageResponse
    fun visibleByHandle(viewerAccountId: UUID?, handle: String, cursor: String?, limit: Int): StoryPageResponse
    fun archive(ownerAccountId: UUID, cursor: String?, limit: Int): StoryPageResponse
    fun createHighlight(ownerAccountId: UUID, request: UpsertStoryHighlightRequest): StoryHighlightResponse
    fun updateHighlight(
        ownerAccountId: UUID,
        highlightId: UUID,
        request: UpsertStoryHighlightRequest
    ): StoryHighlightResponse
    fun deleteHighlight(ownerAccountId: UUID, highlightId: UUID)
    fun highlightsByHandle(
        viewerAccountId: UUID?,
        handle: String,
        cursor: String?,
        limit: Int
    ): StoryHighlightPageResponse
    fun highlight(viewerAccountId: UUID?, highlightId: UUID): StoryHighlightResponse
    fun markSeen(viewerAccountId: UUID, storyId: UUID): StorySeenResponse
    fun viewers(ownerAccountId: UUID, storyId: UUID, cursor: String?, limit: Int): ProfileSummaryPageResponse
    fun ensureVisible(viewerAccountId: UUID?, storyId: UUID)
    fun delete(accountId: UUID, storyId: UUID)
}
