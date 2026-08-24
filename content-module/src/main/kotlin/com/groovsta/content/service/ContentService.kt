package com.groovsta.content.service

import com.groovsta.content.dto.CompleteContentUploadRequest
import com.groovsta.content.dto.ContentResponse
import com.groovsta.content.dto.ContentUploadResponse
import com.groovsta.content.dto.CreateContentUploadRequest
import java.util.UUID

interface ContentService {
    fun createUpload(accountId: UUID, request: CreateContentUploadRequest): ContentUploadResponse
    fun completeUpload(accountId: UUID, contentId: UUID, request: CompleteContentUploadRequest): ContentResponse
    fun get(accountId: UUID?, contentId: UUID): ContentResponse
    fun publish(accountId: UUID, contentId: UUID): ContentResponse
}
