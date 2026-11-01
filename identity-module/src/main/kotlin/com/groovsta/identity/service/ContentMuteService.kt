package com.groovsta.identity.service

import com.groovsta.identity.dto.response.ContentMuteResponse
import com.groovsta.identity.model.ContentMuteType
import java.util.UUID

interface ContentMuteService {
    fun mute(accountId: UUID, targetHandle: String, contentType: ContentMuteType): ContentMuteResponse
    fun unmute(accountId: UUID, targetHandle: String, contentType: ContentMuteType): ContentMuteResponse
    fun mutedAccountIds(accountId: UUID?, contentType: ContentMuteType): Set<UUID>
}
