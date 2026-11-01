package com.groovsta.messaging.repository

import com.groovsta.messaging.model.ChatAttachmentUploadBatch
import com.groovsta.messaging.model.ChatAttachmentUploadStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID

interface ChatAttachmentUploadBatchRepository : JpaRepository<ChatAttachmentUploadBatch, UUID> {
    fun findByIdAndSenderAccountId(id: UUID, senderAccountId: UUID): ChatAttachmentUploadBatch?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select batch from ChatAttachmentUploadBatch batch
        where batch.id = :id and batch.senderAccountId = :senderAccountId
        """
    )
    fun findForUpdateByIdAndSenderAccountId(
        @Param("id") id: UUID,
        @Param("senderAccountId") senderAccountId: UUID
    ): ChatAttachmentUploadBatch?
    fun findBySenderAccountIdAndClientMessageId(
        senderAccountId: UUID,
        clientMessageId: UUID
    ): ChatAttachmentUploadBatch?
    fun findByStatusAndExpiresAtBefore(
        status: ChatAttachmentUploadStatus,
        expiresAt: Instant
    ): List<ChatAttachmentUploadBatch>
}
