package com.groovsta.content.service

import com.groovsta.content.dto.CompleteTranscodeRequest
import com.groovsta.content.dto.TranscodeJobResponse
import java.util.UUID

interface TranscodeOrchestrationService {
    fun claimNext(secret: String): TranscodeJobResponse?
    fun completeJob(secret: String, jobId: UUID, request: CompleteTranscodeRequest)
    fun failJob(secret: String, jobId: UUID, reason: String)
    fun complete(secret: String, reelId: UUID, request: CompleteTranscodeRequest)
    fun fail(secret: String, reelId: UUID, reason: String)
}
