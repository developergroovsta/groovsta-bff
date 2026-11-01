package com.groovsta.content.service

import java.util.UUID

interface TranscodeQueueService {
    fun enqueueChatAttachment(
        attachmentId: UUID,
        sourceObjectKey: String,
        outputPrefix: String
    )
}
