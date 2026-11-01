package com.groovsta.content.service

import com.groovsta.content.dto.ShareResponse
import com.groovsta.content.model.ContentShareChannel
import com.groovsta.identity.dto.response.ProfileSummaryPageResponse
import java.util.UUID

interface ContentShareService {
    fun recordContentShare(accountId: UUID, contentId: UUID, channel: ContentShareChannel): ShareResponse
    fun recordStoryShare(accountId: UUID, storyId: UUID, channel: ContentShareChannel): ShareResponse
    fun contentSharers(contentId: UUID, cursor: String?, limit: Int): ProfileSummaryPageResponse
    fun storySharers(storyId: UUID, cursor: String?, limit: Int): ProfileSummaryPageResponse
    fun countContentShares(contentId: UUID): Long
    fun countStoryShares(storyId: UUID): Long
    fun countContentShares(contentIds: Collection<UUID>): Map<UUID, Long>
    fun countStoryShares(storyIds: Collection<UUID>): Map<UUID, Long>
}
