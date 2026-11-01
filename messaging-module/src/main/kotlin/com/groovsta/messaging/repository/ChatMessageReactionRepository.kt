package com.groovsta.messaging.repository

import com.groovsta.messaging.model.ChatMessageReaction
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ChatMessageReactionRepository : JpaRepository<ChatMessageReaction, UUID> {
    fun findByMessageIdAndAccountId(messageId: UUID, accountId: UUID): ChatMessageReaction?
    fun findByMessageIdIn(messageIds: Collection<UUID>): List<ChatMessageReaction>
}
