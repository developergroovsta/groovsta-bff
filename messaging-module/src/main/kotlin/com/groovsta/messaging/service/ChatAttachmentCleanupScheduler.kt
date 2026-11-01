package com.groovsta.messaging.service

import com.groovsta.messaging.config.ChatMediaProperties
import com.groovsta.messaging.model.ChatAttachmentUploadStatus
import com.groovsta.messaging.repository.ChatAttachmentUploadBatchRepository
import com.groovsta.messaging.repository.ChatMessageAttachmentRepository
import com.groovsta.shared.storage.ObjectStorageService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class ChatAttachmentCleanupScheduler(
    private val batchRepository: ChatAttachmentUploadBatchRepository,
    private val attachmentRepository: ChatMessageAttachmentRepository,
    private val storage: ObjectStorageService,
    private val properties: ChatMediaProperties
) {
    @Scheduled(fixedDelayString = "\${groovsta.chat.media.cleanup-interval:3600000}")
    @Transactional
    fun cleanAbandonedUploads() {
        val cutoff = Instant.now().minus(properties.abandonedUploadRetention)
        batchRepository.findByStatusAndExpiresAtBefore(ChatAttachmentUploadStatus.PENDING, cutoff).forEach { batch ->
            attachmentRepository.findByUploadBatchIdOrderByPosition(batch.id).forEach { attachment ->
                runCatching { storage.delete(attachment.sourceObjectKey) }
            }
            batch.status = ChatAttachmentUploadStatus.EXPIRED
        }
    }
}
