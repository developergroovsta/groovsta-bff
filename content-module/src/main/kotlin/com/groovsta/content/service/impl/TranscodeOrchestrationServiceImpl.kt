package com.groovsta.content.service.impl

import com.groovsta.content.config.ContentProperties
import com.groovsta.content.dto.CompleteTranscodeRequest
import com.groovsta.content.dto.TranscodeJobResponse
import com.groovsta.content.dto.TranscodeTargetType
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.model.StoryStatus
import com.groovsta.content.model.TranscodeJob
import com.groovsta.content.repository.ReelRepository
import com.groovsta.content.repository.StoryRepository
import com.groovsta.content.repository.TranscodeJobRepository
import com.groovsta.content.service.TranscodeOrchestrationService
import com.groovsta.shared.storage.ObjectStorageService
import com.groovsta.shared.media.ChatAttachmentTranscodeHandler
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class TranscodeOrchestrationServiceImpl(
    private val jobRepository: TranscodeJobRepository,
    private val reelRepository: ReelRepository,
    private val storyRepository: StoryRepository,
    private val storage: ObjectStorageService,
    private val properties: ContentProperties,
    private val chatAttachmentHandler: ChatAttachmentTranscodeHandler
) : TranscodeOrchestrationService {

    @Transactional
    override fun claimNext(secret: String): TranscodeJobResponse? {
        authorize(secret)
        val job = jobRepository.findByStatusOrderByCreatedAt("QUEUED", PageRequest.of(0, 1)).firstOrNull() ?: return null
        job.status = "PROCESSING"
        job.claimedAt = Instant.now()
        job.updatedAt = Instant.now()
        val targetType = when {
            job.reelId != null -> TranscodeTargetType.REEL
            job.storyId != null -> TranscodeTargetType.STORY
            else -> TranscodeTargetType.CHAT_ATTACHMENT
        }
        val targetId = job.reelId ?: job.storyId ?: requireNotNull(job.chatAttachmentId)
        return TranscodeJobResponse(
            jobId = job.id,
            targetType = targetType,
            targetId = targetId,
            reelId = job.reelId,
            storyId = job.storyId,
            chatAttachmentId = job.chatAttachmentId,
            sourceObjectKey = job.sourceObjectKey,
            outputPrefix = job.outputPrefix
        )
    }

    @Transactional
    override fun completeJob(secret: String, jobId: UUID, request: CompleteTranscodeRequest) {
        authorize(secret)
        complete(jobRepository.findById(jobId).orElseThrow { notFound() }, request)
    }

    @Transactional
    override fun failJob(secret: String, jobId: UUID, reason: String) {
        authorize(secret)
        fail(jobRepository.findById(jobId).orElseThrow { notFound() }, reason)
    }

    @Transactional
    override fun complete(secret: String, reelId: UUID, request: CompleteTranscodeRequest) {
        authorize(secret)
        val job = jobRepository.findByReelId(reelId) ?: notFound()
        complete(job, request)
    }

    private fun complete(job: TranscodeJob, request: CompleteTranscodeRequest) {
        require(job.status == "PROCESSING") { "Transcode job is not being processed" }
        require(request.publishedObjectKey.startsWith(job.outputPrefix)) { "Transcoded output is outside the assigned output folder" }
        require(storage.exists(request.publishedObjectKey)) { "Transcoded output was not found in S3" }
        request.thumbnailObjectKey?.let {
            require(it.startsWith(job.outputPrefix)) { "Thumbnail is outside the assigned output folder" }
            require(storage.exists(it)) { "Thumbnail was not found in S3" }
        }
        val now = Instant.now()
        when {
            job.reelId != null -> {
                val reel = reelRepository.findById(job.reelId!!).orElseThrow { notFound() }
                require(reel.status == ContentStatus.TRANSCODING) { "Reel is not being transcoded" }
                reel.publishedObjectKey = request.publishedObjectKey
                reel.thumbnailObjectKey = request.thumbnailObjectKey
                reel.durationSeconds = request.durationSeconds
                reel.status = ContentStatus.READY_TO_PUBLISH
                reel.updatedAt = now
            }
            job.storyId != null -> {
                val story = storyRepository.findById(job.storyId!!).orElseThrow { notFound() }
                require(story.status == StoryStatus.TRANSCODING || story.status == StoryStatus.DELETED) {
                    "Story is not being transcoded"
                }
                story.publishedObjectKey = request.publishedObjectKey
                story.thumbnailObjectKey = request.thumbnailObjectKey
                story.durationSeconds = request.durationSeconds
                if (story.status != StoryStatus.DELETED) story.status = StoryStatus.PUBLISHED
                story.updatedAt = now
            }
            else -> chatAttachmentHandler.completeChatAttachment(
                requireNotNull(job.chatAttachmentId),
                request.publishedObjectKey,
                request.thumbnailObjectKey,
                request.durationSeconds,
                request.width,
                request.height
            )
        }
        job.status = "COMPLETED"
        job.updatedAt = now
    }

    @Transactional
    override fun fail(secret: String, reelId: UUID, reason: String) {
        authorize(secret)
        val job = jobRepository.findByReelId(reelId) ?: notFound()
        fail(job, reason)
    }

    private fun fail(job: TranscodeJob, reason: String) {
        val now = Instant.now()
        when {
            job.reelId != null -> {
                val reel = reelRepository.findById(job.reelId!!).orElseThrow { notFound() }
                reel.status = ContentStatus.FAILED
                reel.moderationReason = reason.take(1000)
                reel.updatedAt = now
            }
            job.storyId != null -> {
                val story = storyRepository.findById(job.storyId!!).orElseThrow { notFound() }
                if (story.status != StoryStatus.DELETED) story.status = StoryStatus.FAILED
                story.failureReason = reason.take(1000)
                story.updatedAt = now
            }
            else -> chatAttachmentHandler.failChatAttachment(requireNotNull(job.chatAttachmentId), reason.take(1000))
        }
        job.status = "FAILED"
        job.updatedAt = now
    }

    private fun authorize(secret: String) {
        if (secret != properties.transcodeCallbackSecret) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid transcode worker secret")
        }
    }

    private fun notFound(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Transcode job was not found")
}
