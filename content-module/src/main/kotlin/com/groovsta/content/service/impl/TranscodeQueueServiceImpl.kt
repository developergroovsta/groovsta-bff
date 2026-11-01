package com.groovsta.content.service.impl

import com.groovsta.content.model.TranscodeJob
import com.groovsta.content.repository.TranscodeJobRepository
import com.groovsta.content.service.TranscodeQueueService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class TranscodeQueueServiceImpl(
    private val repository: TranscodeJobRepository
) : TranscodeQueueService {
    @Transactional
    override fun enqueueChatAttachment(
        attachmentId: UUID,
        sourceObjectKey: String,
        outputPrefix: String
    ) {
        if (repository.findByChatAttachmentId(attachmentId) != null) return
        repository.save(
            TranscodeJob(
                chatAttachmentId = attachmentId,
                sourceObjectKey = sourceObjectKey,
                outputPrefix = outputPrefix,
                status = "QUEUED"
            )
        )
    }
}
