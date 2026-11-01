package com.groovsta.messaging.repository

import com.groovsta.messaging.model.ChatMessageAttachment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatMessageAttachmentRepository : JpaRepository<ChatMessageAttachment, UUID> {
    fun findByUploadBatchIdOrderByPosition(uploadBatchId: UUID): List<ChatMessageAttachment>
    fun findByMessageIdInOrderByMessageIdAscPositionAsc(messageIds: Collection<UUID>): List<ChatMessageAttachment>
}
