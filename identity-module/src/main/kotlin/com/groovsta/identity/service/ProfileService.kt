package com.groovsta.identity.service

import com.groovsta.identity.dto.request.CompleteAvatarUploadRequest
import com.groovsta.identity.dto.request.CreateAvatarUploadRequest
import com.groovsta.identity.dto.request.UpdateProfileRequest
import com.groovsta.identity.dto.response.ProfileResponse
import com.groovsta.identity.dto.response.ProfileSearchResponse
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.identity.dto.response.UploadUrlResponse
import java.util.UUID

interface ProfileService {
    fun getByAccountId(accountId: UUID): ProfileResponse
    fun getByHandle(handle: String, viewerAccountId: UUID? = null): ProfileResponse
    fun summaries(accountIds: Set<UUID>): Map<UUID, ProfileSummaryResponse>
    fun mentionableSummaries(accountIds: Set<UUID>): Map<UUID, ProfileSummaryResponse>
    fun searchByHandleText(
        query: String,
        cursor: String?,
        limit: Int,
        viewerAccountId: UUID? = null
    ): ProfileSearchResponse
    fun update(accountId: UUID, request: UpdateProfileRequest): ProfileResponse
    fun createAvatarUpload(accountId: UUID, request: CreateAvatarUploadRequest): UploadUrlResponse
    fun completeAvatarUpload(accountId: UUID, request: CompleteAvatarUploadRequest): ProfileResponse
}
