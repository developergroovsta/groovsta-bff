package com.groovsta.content.service.impl

import com.groovsta.content.dto.CompleteContentUploadRequest
import com.groovsta.content.dto.CompletePostUploadRequest
import com.groovsta.content.dto.CompleteReelUploadRequest
import com.groovsta.content.dto.ContentMediaUploadResponse
import com.groovsta.content.dto.ContentResponse
import com.groovsta.content.dto.ContentUploadResponse
import com.groovsta.content.dto.CreateContentUploadRequest
import com.groovsta.content.dto.CreatePostMediaUploadRequest
import com.groovsta.content.dto.CreatePostUploadRequest
import com.groovsta.content.dto.CreateReelUploadRequest
import com.groovsta.content.model.ContentKind
import com.groovsta.content.model.Post
import com.groovsta.content.model.Reel
import com.groovsta.content.repository.ContentItemRepository
import com.groovsta.content.service.ContentService
import com.groovsta.content.service.PostService
import com.groovsta.content.service.ReelService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ContentServiceImpl(
    private val contentRepository: ContentItemRepository,
    private val postService: PostService,
    private val reelService: ReelService
) : ContentService {

    override fun createUpload(accountId: UUID, request: CreateContentUploadRequest): ContentUploadResponse =
        when (request.type) {
            ContentKind.POST -> createPostUpload(accountId, request)
            ContentKind.REEL -> createReelUpload(accountId, request)
        }

    @Transactional
    override fun completeUpload(
        accountId: UUID,
        contentId: UUID,
        request: CompleteContentUploadRequest
    ): ContentResponse = when (content(contentId)) {
        is Post -> postResponse(postService.completeUpload(accountId, contentId, CompletePostUploadRequest(request.objectKeys)))
        is Reel -> {
            require(request.objectKeys.size == 1) { "A reel completion requires exactly one object key" }
            reelResponse(reelService.completeUpload(accountId, contentId, CompleteReelUploadRequest(request.objectKeys.single())))
        }
        else -> unsupported()
    }

    @Transactional(readOnly = true)
    override fun get(accountId: UUID?, contentId: UUID): ContentResponse = when (content(contentId)) {
        is Post -> postResponse(postService.get(accountId, contentId))
        is Reel -> reelResponse(reelService.get(accountId, contentId))
        else -> unsupported()
    }

    @Transactional
    override fun publish(accountId: UUID, contentId: UUID): ContentResponse = when (content(contentId)) {
        is Reel -> reelResponse(reelService.publish(accountId, contentId))
        is Post -> throw IllegalArgumentException("Image posts are published when their uploads complete")
        else -> unsupported()
    }

    private fun createPostUpload(accountId: UUID, request: CreateContentUploadRequest): ContentUploadResponse {
        require(request.media.all { it.contentType in imageContentTypes }) {
            "A post accepts only JPEG, PNG, or WebP images"
        }
        val response = postService.createUpload(
            accountId,
            CreatePostUploadRequest(
                caption = request.caption,
                media = request.media.map {
                    CreatePostMediaUploadRequest(it.fileName, it.contentType, it.sizeBytes)
                },
                taggedAccountIds = request.taggedAccountIds
            )
        )
        return ContentUploadResponse(
            contentId = response.postId,
            type = ContentKind.POST,
            uploads = response.uploads.map {
                ContentMediaUploadResponse(it.position, it.objectKey, it.uploadUrl, it.expiresAt)
            }
        )
    }

    private fun createReelUpload(accountId: UUID, request: CreateContentUploadRequest): ContentUploadResponse {
        require(request.media.size == 1) { "A reel requires exactly one video" }
        val media = request.media.single()
        require(media.contentType in videoContentTypes) { "A reel must be MP4, QuickTime, or WebM video" }
        val response = reelService.createUpload(
            accountId,
            CreateReelUploadRequest(media.fileName, media.contentType, media.sizeBytes, request.caption, request.taggedAccountIds)
        )
        return ContentUploadResponse(
            contentId = response.reelId,
            type = ContentKind.REEL,
            uploads = listOf(ContentMediaUploadResponse(0, response.objectKey, response.uploadUrl, response.expiresAt))
        )
    }

    private fun content(contentId: UUID) = contentRepository.findById(contentId).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Content was not found")
    }

    private fun postResponse(post: com.groovsta.content.dto.PostResponse) =
        ContentResponse(post.id, ContentKind.POST, post, null)

    private fun reelResponse(reel: com.groovsta.content.dto.ReelResponse) =
        ContentResponse(reel.id, ContentKind.REEL, null, reel)

    private fun unsupported(): Nothing = error("Unsupported content type")

    private companion object {
        val imageContentTypes = setOf("image/jpeg", "image/png", "image/webp")
        val videoContentTypes = setOf("video/mp4", "video/quicktime", "video/webm")
    }
}
