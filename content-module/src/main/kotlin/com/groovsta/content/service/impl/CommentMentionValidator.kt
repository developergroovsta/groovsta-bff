package com.groovsta.content.service.impl

import com.groovsta.content.dto.CreateCommentMentionRequest
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import java.util.UUID

internal object CommentMentionValidator {
    fun validate(
        text: String,
        mentions: List<CreateCommentMentionRequest>,
        profiles: Map<UUID, ProfileSummaryResponse>
    ) {
        require(profiles.size == mentions.map { it.accountId }.toSet().size) {
            "Every mentioned account must exist and be active"
        }
        var previousEnd = -1
        mentions.sortedBy { it.start }.forEach { mention ->
            require(mention.endExclusive <= text.length && mention.endExclusive > mention.start) {
                "Mention range is outside the comment text"
            }
            require(mention.start >= previousEnd) { "Mention ranges must not overlap" }
            val expected = "@${requireNotNull(profiles[mention.accountId]).handle}"
            require(text.substring(mention.start, mention.endExclusive).equals(expected, ignoreCase = true)) {
                "Mention range must point to $expected"
            }
            previousEnd = mention.endExclusive
        }
    }
}
