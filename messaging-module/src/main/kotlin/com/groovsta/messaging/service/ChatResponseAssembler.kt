package com.groovsta.messaging.service

import com.groovsta.identity.service.ProfileService
import com.groovsta.messaging.dto.ChatDeliveryStatus
import com.groovsta.messaging.dto.ChatAttachmentResponse
import com.groovsta.messaging.dto.ChatMessageResponse
import com.groovsta.messaging.dto.ChatReactionSummaryResponse
import com.groovsta.messaging.dto.ChatReplyPreviewResponse
import com.groovsta.messaging.model.ChatConversation
import com.groovsta.messaging.model.ChatConversationMember
import com.groovsta.messaging.model.ChatMessage
import com.groovsta.messaging.model.ChatAttachmentMediaType
import com.groovsta.messaging.model.ChatAttachmentStatus
import com.groovsta.messaging.model.ChatMessageType
import com.groovsta.messaging.repository.ChatMessageReactionRepository
import com.groovsta.messaging.repository.ChatMessageAttachmentRepository
import com.groovsta.shared.storage.ObjectStorageService
import com.groovsta.shared.storage.S3StorageProperties
import com.groovsta.messaging.repository.ChatMessageRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ChatResponseAssembler(
    private val profileService: ProfileService,
    private val contentPreviewService: ChatContentPreviewService,
    private val messageRepository: ChatMessageRepository,
    private val reactionRepository: ChatMessageReactionRepository,
    private val attachmentRepository: ChatMessageAttachmentRepository,
    private val storage: ObjectStorageService,
    private val storageProperties: S3StorageProperties
) {
    fun messages(
        viewerAccountId: UUID,
        messages: Collection<ChatMessage>,
        conversations: Map<UUID, ChatConversation>,
        members: Collection<ChatConversationMember>
    ): Map<UUID, ChatMessageResponse> {
        if (messages.isEmpty()) return emptyMap()

        val replyMessages = messageRepository.findAllById(
            messages.mapNotNullTo(mutableSetOf(), ChatMessage::replyToMessageId)
        ).associateBy(ChatMessage::id)
        val senderIds = messages.mapTo(mutableSetOf()) { it.senderAccountId }
        replyMessages.values.mapTo(senderIds) { it.senderAccountId }
        val senderProfiles = profileService.summaries(senderIds)
        val previewMessages = messages.filter { it.unsentAt == null } +
            replyMessages.values.filter { it.unsentAt == null }
        val sharedContent = contentPreviewService.previews(
            viewerAccountId,
            previewMessages.asSequence().mapNotNull(ChatMessage::sharedContentId).toSet()
        )
        val reactionsByMessage = reactionRepository.findByMessageIdIn(messages.map(ChatMessage::id))
            .groupBy { it.messageId }
        val previewMessageIds = previewMessages.map(ChatMessage::id).toSet()
        val attachmentResponsesByMessage = if (previewMessageIds.isEmpty()) emptyMap() else
            attachmentRepository.findByMessageIdInOrderByMessageIdAscPositionAsc(previewMessageIds)
                .groupBy { requireNotNull(it.messageId) }
                .mapValues { (_, attachments) ->
                attachments.map { attachment ->
                    val mediaKey = when (attachment.status) {
                        ChatAttachmentStatus.READY -> attachment.mediaObjectKey ?: attachment.sourceObjectKey
                        ChatAttachmentStatus.PROCESSING -> attachment.sourceObjectKey
                        ChatAttachmentStatus.UPLOADING,
                        ChatAttachmentStatus.FAILED -> null
                    }
                    val mediaUrl = mediaKey?.let {
                        storage.createDownloadUrl(it, storageProperties.downloadUrlTtl)
                    }
                    val thumbnailUrl = attachment.thumbnailObjectKey?.let {
                        storage.createDownloadUrl(it, storageProperties.downloadUrlTtl)
                    } ?: mediaUrl.takeIf { attachment.mediaType == ChatAttachmentMediaType.IMAGE }
                    ChatAttachmentResponse(
                        id = attachment.id,
                        mediaType = attachment.mediaType,
                        status = attachment.status,
                        mediaUrl = mediaUrl,
                        thumbnailUrl = thumbnailUrl,
                        width = attachment.width,
                        height = attachment.height,
                        durationMillis = attachment.durationMillis,
                        contentType = attachment.contentType,
                        sizeBytes = attachment.actualSizeBytes ?: attachment.expectedSizeBytes
                    )
                }
            }
        val memberByConversationAndAccount = members.associateBy { it.conversationId to it.accountId }

        return messages.associate { message ->
            val conversation = requireNotNull(conversations[message.conversationId]) {
                "Conversation was not loaded for message"
            }
            val recipientId = conversation.otherParticipant(message.senderAccountId)
            val recipientReadAt = memberByConversationAndAccount[conversation.id to recipientId]?.lastReadAt
            val isRead = recipientReadAt != null && !recipientReadAt.isBefore(message.createdAt)
            val unsent = message.unsentAt != null
            message.id to ChatMessageResponse(
                id = message.id,
                conversationId = message.conversationId,
                sender = requireNotNull(senderProfiles[message.senderAccountId]) {
                    "Message sender profile was not found"
                },
                type = message.type,
                text = message.text.takeUnless { unsent },
                sharedContent = if (!unsent && message.type == ChatMessageType.CONTENT_SHARE) {
                    message.sharedContentId?.let(sharedContent::get)
                } else {
                    null
                },
                attachments = if (!unsent && message.type == ChatMessageType.MEDIA) {
                    attachmentResponsesByMessage[message.id].orEmpty()
                } else {
                    emptyList()
                },
                replyTo = if (unsent) null else message.replyToMessageId
                    ?.let(replyMessages::get)
                    ?.let { reply ->
                        val replyUnsent = reply.unsentAt != null
                        ChatReplyPreviewResponse(
                            id = reply.id,
                            sender = requireNotNull(senderProfiles[reply.senderAccountId]),
                            type = reply.type,
                            text = reply.text.takeUnless { replyUnsent },
                            sharedContent = if (!replyUnsent && reply.type == ChatMessageType.CONTENT_SHARE) {
                                reply.sharedContentId?.let(sharedContent::get)
                            } else {
                                null
                            },
                            attachments = if (!replyUnsent && reply.type == ChatMessageType.MEDIA) {
                                attachmentResponsesByMessage[reply.id].orEmpty()
                            } else {
                                emptyList()
                            },
                            unsent = replyUnsent
                        )
                    },
                reactions = if (unsent) emptyList() else reactionSummaries(
                    viewerAccountId,
                    reactionsByMessage[message.id].orEmpty()
                ),
                deliveryStatus = when {
                    isRead -> ChatDeliveryStatus.SEEN
                    message.deliveredAt != null -> ChatDeliveryStatus.DELIVERED
                    else -> ChatDeliveryStatus.SENT
                },
                isRead = isRead,
                sentAt = message.createdAt,
                editedAt = message.editedAt,
                unsent = unsent,
                unsentAt = message.unsentAt
            )
        }
    }

    private fun reactionSummaries(
        viewerAccountId: UUID,
        reactions: Collection<com.groovsta.messaging.model.ChatMessageReaction>
    ): List<ChatReactionSummaryResponse> = reactions
        .groupBy { it.emoji }
        .map { (emoji, matching) ->
            ChatReactionSummaryResponse(
                emoji = emoji,
                count = matching.size,
                reactedByViewer = matching.any { it.accountId == viewerAccountId }
            )
        }
        .sortedWith(compareByDescending<ChatReactionSummaryResponse> { it.count }.thenBy { it.emoji })
}
