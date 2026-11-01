package com.groovsta.content.service

import com.groovsta.content.dto.CompleteReelUploadRequest
import com.groovsta.content.dto.CreateReelUploadRequest
import com.groovsta.content.dto.ReelResponse
import com.groovsta.content.dto.ReelUploadResponse
import java.util.UUID

interface ReelService {
    fun createUpload(accountId: UUID, request: CreateReelUploadRequest): ReelUploadResponse
    fun completeUpload(accountId: UUID, reelId: UUID, request: CompleteReelUploadRequest): ReelResponse
    fun get(accountId: UUID?, reelId: UUID): ReelResponse
    fun getAll(accountId: UUID?, reelIds: Collection<UUID>): Map<UUID, ReelResponse>
    fun publish(accountId: UUID, reelId: UUID): ReelResponse
}
