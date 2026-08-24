package com.groovsta.content.service

import com.groovsta.content.dto.SavedContentPageResponse
import com.groovsta.content.dto.SavedContentResponse
import java.util.UUID

interface SavedContentService {
    fun save(accountId: UUID, contentId: UUID): SavedContentResponse
    fun unsave(accountId: UUID, contentId: UUID): SavedContentResponse
    fun savedPosts(accountId: UUID, cursor: String?, limit: Int): SavedContentPageResponse
    fun savedReels(accountId: UUID, cursor: String?, limit: Int): SavedContentPageResponse
}
