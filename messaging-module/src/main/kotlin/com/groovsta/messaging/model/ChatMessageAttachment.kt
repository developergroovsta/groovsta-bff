package com.groovsta.messaging.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "chat_message_attachments",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_chat_attachment_batch_position",
            columnNames = ["upload_batch_id", "position"]
        ),
        UniqueConstraint(
            name = "uq_chat_attachment_message_position",
            columnNames = ["message_id", "position"]
        )
    ]
)
class ChatMessageAttachment(
    id: UUID = UUID.randomUUID(),
    @Column(name = "upload_batch_id", nullable = false)
    var uploadBatchId: UUID = UUID.randomUUID(),
    @Column(name = "message_id")
    var messageId: UUID? = null,
    @Column(nullable = false)
    var position: Int = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    var mediaType: ChatAttachmentMediaType = ChatAttachmentMediaType.IMAGE,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ChatAttachmentStatus = ChatAttachmentStatus.UPLOADING,
    @Column(name = "source_object_key", nullable = false, length = 1024, unique = true)
    var sourceObjectKey: String = "",
    @Column(name = "media_object_key", length = 1024)
    var mediaObjectKey: String? = null,
    @Column(name = "thumbnail_object_key", length = 1024)
    var thumbnailObjectKey: String? = null,
    @Column(name = "file_name", nullable = false, length = 255)
    var fileName: String = "",
    @Column(name = "content_type", nullable = false, length = 100)
    var contentType: String = "",
    @Column(name = "expected_size_bytes", nullable = false)
    var expectedSizeBytes: Long = 0,
    @Column(name = "actual_size_bytes")
    var actualSizeBytes: Long? = null,
    @Column(nullable = false)
    var width: Int = 1,
    @Column(nullable = false)
    var height: Int = 1,
    @Column(name = "duration_millis")
    var durationMillis: Long? = null,
    @Column(name = "failure_reason", length = 1000)
    var failureReason: String? = null,
    createdAt: Instant = Instant.EPOCH,
    updatedAt: Instant = Instant.EPOCH
) : MessagingModel(id, createdAt, updatedAt)
