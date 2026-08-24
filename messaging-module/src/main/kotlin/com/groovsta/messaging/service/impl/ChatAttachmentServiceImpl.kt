package com.groovsta.messaging.service.impl

import com.groovsta.content.service.TranscodeQueueService
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.messaging.config.ChatMediaProperties
import com.groovsta.messaging.dto.ChatAttachmentUploadItemResponse
import com.groovsta.messaging.dto.ChatAttachmentUploadResponse
import com.groovsta.messaging.dto.ChatEventType
import com.groovsta.messaging.dto.ChatMessageResponse
import com.groovsta.messaging.dto.CreateChatAttachmentRequest
import com.groovsta.messaging.dto.CreateChatAttachmentUploadRequest
import com.groovsta.messaging.model.ChatAttachmentMediaType
import com.groovsta.messaging.model.ChatAttachmentStatus
import com.groovsta.messaging.model.ChatAttachmentUploadBatch
import com.groovsta.messaging.model.ChatAttachmentUploadStatus
import com.groovsta.messaging.model.ChatMessage
import com.groovsta.messaging.model.ChatMessageAttachment
import com.groovsta.messaging.model.ChatMessageType
import com.groovsta.messaging.repository.ChatAttachmentUploadBatchRepository
import com.groovsta.messaging.repository.ChatConversationMemberRepository
import com.groovsta.messaging.repository.ChatConversationRepository
import com.groovsta.messaging.repository.ChatMessageAttachmentRepository
import com.groovsta.messaging.repository.ChatMessageRepository
import com.groovsta.messaging.service.ChatAccessService
import com.groovsta.messaging.service.ChatAttachmentService
import com.groovsta.messaging.service.ChatMessageEventPublisher
import com.groovsta.shared.media.ChatAttachmentTranscodeHandler
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import com.groovsta.shared.storage.ObjectStorageService
import com.groovsta.shared.storage.S3StorageProperties
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Service
class ChatAttachmentServiceImpl(
    private val batchRepository: ChatAttachmentUploadBatchRepository,
    private val attachmentRepository: ChatMessageAttachmentRepository,
    private val messageRepository: ChatMessageRepository,
    private val conversationRepository: ChatConversationRepository,
    private val memberRepository: ChatConversationMemberRepository,
    private val accessService: ChatAccessService,
    private val messageEventPublisher: ChatMessageEventPublisher,
    private val transcodeQueueService: TranscodeQueueService,
    private val notificationPublisher: NotificationPublisher,
    private val storage: ObjectStorageService,
    private val storageProperties: S3StorageProperties,
    private val properties: ChatMediaProperties
) : ChatAttachmentService, ChatAttachmentTranscodeHandler {

    @Transactional
    override fun createUpload(
        accountId: UUID,
        request: CreateChatAttachmentUploadRequest
    ): ChatAttachmentUploadResponse {
        val normalized = normalizeAndValidate(request)
        val existingBatch = batchRepository.findBySenderAccountIdAndClientMessageId(
            accountId,
            request.clientMessageId
        )
        if (existingBatch != null) return existingUpload(accountId, normalized, existingBatch)
        if (messageRepository.findBySenderAccountIdAndClientMessageId(accountId, request.clientMessageId) != null) {
            throw IdentityConflictException("clientMessageId was already used for a different message")
        }

        accessService.requireCanMessage(accountId, request.recipientAccountId)
        val conversation = accessService.directConversation(accountId, request.recipientAccountId)
        accessService.validateReply(conversation.id, request.replyToMessageId)
        val now = Instant.now()
        val expiresAt = now.plus(storageProperties.uploadUrlTtl)
        val batch = batchRepository.saveAndFlush(
            ChatAttachmentUploadBatch(
                senderAccountId = accountId,
                recipientAccountId = request.recipientAccountId,
                clientMessageId = request.clientMessageId,
                text = normalized.text,
                replyToMessageId = request.replyToMessageId,
                expiresAt = expiresAt
            )
        )
        val attachments = normalized.attachments.mapIndexed { position, media ->
            val id = UUID.randomUUID()
            ChatMessageAttachment(
                id = id,
                uploadBatchId = batch.id,
                position = position,
                mediaType = media.mediaType,
                sourceObjectKey = sourceObjectKey(accountId, batch.id, id, media),
                fileName = media.fileName.trim(),
                contentType = normalizeContentType(media.contentType),
                expectedSizeBytes = media.sizeBytes,
                width = media.width,
                height = media.height,
                durationMillis = media.durationMillis
            )
        }.let(attachmentRepository::saveAll)
        return uploadResponse(batch, attachments)
    }

    @Transactional
    override fun completeUpload(accountId: UUID, uploadBatchId: UUID): ChatMessageResponse {
        val batch = batchRepository.findForUpdateByIdAndSenderAccountId(uploadBatchId, accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Chat attachment upload was not found")
        if (batch.status == ChatAttachmentUploadStatus.COMPLETED) {
            return completedResponse(accountId, batch)
        }
        if (batch.status != ChatAttachmentUploadStatus.PENDING || Instant.now().isAfter(batch.expiresAt)) {
            throw ResponseStatusException(
                HttpStatus.GONE,
                "Chat attachment upload expired; initiate it again with the same clientMessageId"
            )
        }

        accessService.requireCanMessage(accountId, batch.recipientAccountId)
        val conversation = accessService.directConversation(accountId, batch.recipientAccountId)
        accessService.validateReply(conversation.id, batch.replyToMessageId)
        val attachments = attachmentRepository.findByUploadBatchIdOrderByPosition(batch.id)
        if (attachments.isEmpty()) throw IllegalStateException("Chat attachment upload has no attachments")
        val metadataByAttachment = attachments.associateWith { attachment ->
            val metadata = storage.metadata(attachment.sourceObjectKey).orElseThrow {
                ResponseStatusException(HttpStatus.CONFLICT, "Attachment ${attachment.position} was not uploaded")
            }
            val actualContentType = normalizeContentType(metadata.contentType() ?: "")
            if (actualContentType != attachment.contentType) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Attachment ${attachment.position} content type does not match the upload request"
                )
            }
            if (metadata.sizeBytes() != attachment.expectedSizeBytes) {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Attachment ${attachment.position} size does not match the upload request"
                )
            }
            metadata
        }

        val existing = messageRepository.findBySenderAccountIdAndClientMessageId(accountId, batch.clientMessageId)
        val message = existing?.also {
            val sameRequest = it.type == ChatMessageType.MEDIA &&
                it.conversationId == conversation.id &&
                it.text == batch.text &&
                it.replyToMessageId == batch.replyToMessageId
            if (!sameRequest) {
                throw IdentityConflictException("clientMessageId was already used for a different message")
            }
        } ?: messageRepository.saveAndFlush(
            ChatMessage(
                conversationId = conversation.id,
                senderAccountId = accountId,
                clientMessageId = batch.clientMessageId,
                type = ChatMessageType.MEDIA,
                text = batch.text,
                replyToMessageId = batch.replyToMessageId
            )
        )

        attachments.forEach { attachment ->
            attachment.messageId = message.id
            attachment.actualSizeBytes = metadataByAttachment.getValue(attachment).sizeBytes()
            attachment.status = if (attachment.mediaType == ChatAttachmentMediaType.IMAGE) {
                attachment.mediaObjectKey = attachment.sourceObjectKey
                ChatAttachmentStatus.READY
            } else {
                ChatAttachmentStatus.PROCESSING
            }
        }
        attachmentRepository.saveAllAndFlush(attachments)
        val completedAt = Instant.now()
        batch.messageId = message.id
        batch.status = ChatAttachmentUploadStatus.COMPLETED
        batch.completedAt = completedAt
        batchRepository.saveAndFlush(batch)

        conversationRepository.advanceLastMessage(conversation.id, message.id, message.createdAt)
        memberRepository.advanceReadPosition(
            conversationId = conversation.id,
            accountId = accountId,
            messageId = message.id,
            messageCreatedAt = message.createdAt,
            updatedAt = completedAt
        )
        attachments.filter { it.mediaType == ChatAttachmentMediaType.VIDEO }.forEach { attachment ->
            transcodeQueueService.enqueueChatAttachment(
                attachment.id,
                attachment.sourceObjectKey,
                videoOutputPrefix(batch, attachment)
            )
        }

        notificationPublisher.publish(
            NotificationCommand(
                batch.recipientAccountId,
                accountId,
                NotificationType.DIRECT_MESSAGE,
                NotificationTargetType.CONVERSATION,
                conversation.id,
                message.id,
                notificationPreview(attachments)
            )
        )
        return messageEventPublisher.changed(accountId, message, conversation, ChatEventType.MESSAGE_CREATED)
    }

    @Transactional
    override fun completeChatAttachment(
        attachmentId: UUID,
        mediaObjectKey: String,
        thumbnailObjectKey: String?,
        durationSeconds: Int,
        width: Int?,
        height: Int?
    ) {
        val attachment = attachmentRepository.findById(attachmentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Chat attachment was not found")
        }
        if (attachment.status == ChatAttachmentStatus.READY) return
        if (attachment.status != ChatAttachmentStatus.PROCESSING) {
            throw IdentityConflictException("Chat attachment is not being processed")
        }
        attachment.mediaObjectKey = mediaObjectKey
        attachment.thumbnailObjectKey = thumbnailObjectKey
        attachment.durationMillis = durationSeconds * 1_000L
        width?.let { attachment.width = it }
        height?.let { attachment.height = it }
        attachment.contentType = "video/mp4"
        attachment.status = ChatAttachmentStatus.READY
        attachment.failureReason = null
        attachmentRepository.saveAndFlush(attachment)
        publishAttachmentUpdate(attachment)
    }

    @Transactional
    override fun failChatAttachment(attachmentId: UUID, reason: String) {
        val attachment = attachmentRepository.findById(attachmentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Chat attachment was not found")
        }
        if (attachment.status == ChatAttachmentStatus.FAILED) return
        if (attachment.status != ChatAttachmentStatus.PROCESSING) {
            throw IdentityConflictException("Chat attachment is not being processed")
        }
        attachment.status = ChatAttachmentStatus.FAILED
        attachment.failureReason = reason.take(1000)
        attachmentRepository.saveAndFlush(attachment)
        publishAttachmentUpdate(attachment)
    }

    private fun publishAttachmentUpdate(attachment: ChatMessageAttachment) {
        val message = messageRepository.findById(requireNotNull(attachment.messageId)).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Chat message was not found")
        }
        val conversation = conversationRepository.findById(message.conversationId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation was not found")
        }
        messageEventPublisher.changed(message.senderAccountId, message, conversation, ChatEventType.MESSAGE_UPDATED)
    }

    private fun existingUpload(
        accountId: UUID,
        request: NormalizedUploadRequest,
        batch: ChatAttachmentUploadBatch
    ): ChatAttachmentUploadResponse {
        val attachments = attachmentRepository.findByUploadBatchIdOrderByPosition(batch.id)
        val sameRequest = batch.recipientAccountId == request.recipientAccountId &&
            batch.text == request.text &&
            batch.replyToMessageId == request.replyToMessageId &&
            sameAttachments(attachments, request.attachments)
        if (!sameRequest) {
            throw IdentityConflictException("clientMessageId was already used for a different media message")
        }
        if (batch.status != ChatAttachmentUploadStatus.COMPLETED) {
            accessService.requireCanMessage(accountId, batch.recipientAccountId)
            val conversation = accessService.directConversation(accountId, batch.recipientAccountId)
            accessService.validateReply(conversation.id, batch.replyToMessageId)
            batch.status = ChatAttachmentUploadStatus.PENDING
            batch.expiresAt = Instant.now().plus(storageProperties.uploadUrlTtl)
            batchRepository.save(batch)
        }
        return uploadResponse(batch, attachments)
    }

    private fun completedResponse(accountId: UUID, batch: ChatAttachmentUploadBatch): ChatMessageResponse {
        val message = messageRepository.findById(requireNotNull(batch.messageId)).orElseThrow {
            IllegalStateException("Completed chat attachment upload has no message")
        }
        val conversation = accessService.participantConversation(message.conversationId, accountId)
        return messageEventPublisher.response(accountId, message, conversation)
    }

    private fun uploadResponse(
        batch: ChatAttachmentUploadBatch,
        attachments: List<ChatMessageAttachment>
    ): ChatAttachmentUploadResponse {
        val completed = batch.status == ChatAttachmentUploadStatus.COMPLETED
        val uploads = attachments.map { attachment ->
            val upload = if (completed) null else storage.createUpload(
                attachment.sourceObjectKey,
                attachment.contentType,
                storageProperties.uploadUrlTtl
            )
            ChatAttachmentUploadItemResponse(
                attachmentId = attachment.id,
                position = attachment.position,
                mediaType = attachment.mediaType,
                uploadUrl = upload?.uploadUrl(),
                requiredHeaders = if (upload == null) emptyMap() else mapOf("Content-Type" to attachment.contentType),
                expiresAt = upload?.expiresAt()
            )
        }
        return ChatAttachmentUploadResponse(
            uploadBatchId = batch.id,
            clientMessageId = batch.clientMessageId,
            status = batch.status,
            messageId = batch.messageId,
            uploads = uploads,
            expiresAt = batch.expiresAt
        )
    }

    private fun normalizeAndValidate(request: CreateChatAttachmentUploadRequest): NormalizedUploadRequest {
        if (request.attachments.isEmpty() || request.attachments.size > properties.maxAttachmentsPerMessage) {
            throw IllegalArgumentException(
                "A media message requires 1 to ${properties.maxAttachmentsPerMessage} attachments"
            )
        }
        var totalBytes = 0L
        request.attachments.forEachIndexed { position, attachment ->
            val contentType = normalizeContentType(attachment.contentType)
            val allowed = allowedContentTypes.getValue(attachment.mediaType)
            if (contentType !in allowed) {
                throw IllegalArgumentException("Attachment $position has an unsupported content type")
            }
            val maxBytes = when (attachment.mediaType) {
                ChatAttachmentMediaType.IMAGE -> properties.maxImageBytes
                ChatAttachmentMediaType.VIDEO -> properties.maxVideoBytes
            }
            if (attachment.sizeBytes !in 1..maxBytes) {
                throw IllegalArgumentException("Attachment $position exceeds the ${attachment.mediaType.name.lowercase()} size limit")
            }
            totalBytes = try {
                Math.addExact(totalBytes, attachment.sizeBytes)
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("Attachment batch size is invalid")
            }
            if (attachment.width !in 1..properties.maxDimensionPixels ||
                attachment.height !in 1..properties.maxDimensionPixels
            ) {
                throw IllegalArgumentException("Attachment $position dimensions exceed the supported limit")
            }
            if (attachment.mediaType == ChatAttachmentMediaType.IMAGE && attachment.durationMillis != null) {
                throw IllegalArgumentException("Image attachments cannot have a duration")
            }
            if (attachment.mediaType == ChatAttachmentMediaType.VIDEO &&
                attachment.durationMillis != null &&
                attachment.durationMillis > properties.maxVideoDuration.toMillis()
            ) {
                throw IllegalArgumentException("Attachment $position exceeds the video duration limit")
            }
        }
        if (totalBytes > properties.maxBatchBytes) {
            throw IllegalArgumentException("The combined attachment size exceeds the message limit")
        }
        return NormalizedUploadRequest(
            recipientAccountId = request.recipientAccountId,
            text = request.text?.trim()?.takeIf(String::isNotEmpty),
            replyToMessageId = request.replyToMessageId,
            attachments = request.attachments
        )
    }

    private fun sameAttachments(
        existing: List<ChatMessageAttachment>,
        requested: List<CreateChatAttachmentRequest>
    ): Boolean = existing.size == requested.size && existing.zip(requested).withIndex().all { (position, pair) ->
        val (stored, request) = pair
        stored.position == position &&
            stored.mediaType == request.mediaType &&
            stored.fileName == request.fileName.trim() &&
            stored.contentType == normalizeContentType(request.contentType) &&
            stored.expectedSizeBytes == request.sizeBytes &&
            stored.width == request.width &&
            stored.height == request.height &&
            stored.durationMillis == request.durationMillis
    }

    private fun sourceObjectKey(
        accountId: UUID,
        batchId: UUID,
        attachmentId: UUID,
        request: CreateChatAttachmentRequest
    ): String {
        val prefix = when (request.mediaType) {
            ChatAttachmentMediaType.IMAGE -> storageProperties.imagePrefix
            ChatAttachmentMediaType.VIDEO -> storageProperties.videoPrefix
        }.trim('/')
        val extension = extensions.getValue(normalizeContentType(request.contentType))
        return "$prefix/chat/$accountId/$batchId/$attachmentId/source.$extension"
    }

    private fun videoOutputPrefix(
        batch: ChatAttachmentUploadBatch,
        attachment: ChatMessageAttachment
    ): String = "${storageProperties.videoPrefix.trim('/')}/chat/${batch.senderAccountId}/${batch.id}/${attachment.id}/published/"

    private fun notificationPreview(attachments: List<ChatMessageAttachment>): String {
        val images = attachments.count { it.mediaType == ChatAttachmentMediaType.IMAGE }
        val videos = attachments.size - images
        return when {
            attachments.size == 1 && images == 1 -> "Sent a photo"
            attachments.size == 1 -> "Sent a video"
            videos == 0 -> "Sent $images photos"
            images == 0 -> "Sent $videos videos"
            else -> "Sent ${attachments.size} photos and videos"
        }
    }

    private fun normalizeContentType(contentType: String): String =
        contentType.substringBefore(';').trim().lowercase(Locale.ROOT)

    private data class NormalizedUploadRequest(
        val recipientAccountId: UUID,
        val text: String?,
        val replyToMessageId: UUID?,
        val attachments: List<CreateChatAttachmentRequest>
    )

    private companion object {
        val allowedContentTypes = mapOf(
            ChatAttachmentMediaType.IMAGE to setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif"),
            ChatAttachmentMediaType.VIDEO to setOf("video/mp4", "video/quicktime", "video/webm")
        )
        val extensions = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/heic" to "heic",
            "image/heif" to "heif",
            "video/mp4" to "mp4",
            "video/quicktime" to "mov",
            "video/webm" to "webm"
        )
    }
}
