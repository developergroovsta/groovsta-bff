package com.groovsta.messaging.service

import com.groovsta.messaging.dto.ChatAttachmentUploadResponse
import com.groovsta.messaging.dto.ChatMessageResponse
import com.groovsta.messaging.dto.CreateChatAttachmentUploadRequest
import java.util.UUID

interface ChatAttachmentService {
    fun createUpload(accountId: UUID, request: CreateChatAttachmentUploadRequest): ChatAttachmentUploadResponse
    fun completeUpload(accountId: UUID, uploadBatchId: UUID): ChatMessageResponse
}
