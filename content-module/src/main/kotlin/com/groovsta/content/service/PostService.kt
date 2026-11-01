package com.groovsta.content.service

import com.groovsta.content.dto.CompletePostUploadRequest
import com.groovsta.content.dto.CreatePostUploadRequest
import com.groovsta.content.dto.PostResponse
import com.groovsta.content.dto.PostUploadResponse
import java.util.UUID

interface PostService {
    fun createUpload(accountId: UUID, request: CreatePostUploadRequest): PostUploadResponse
    fun completeUpload(accountId: UUID, postId: UUID, request: CompletePostUploadRequest): PostResponse
    fun get(accountId: UUID?, postId: UUID): PostResponse
    fun getAll(accountId: UUID?, postIds: Collection<UUID>): Map<UUID, PostResponse>
}
