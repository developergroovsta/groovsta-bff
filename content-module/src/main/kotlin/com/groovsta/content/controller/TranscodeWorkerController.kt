package com.groovsta.content.controller

import com.groovsta.content.dto.CompleteTranscodeRequest
import com.groovsta.content.service.TranscodeOrchestrationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/transcode")
class TranscodeWorkerController(private val service: TranscodeOrchestrationService) {
    @PostMapping("/jobs/claim")
    fun claim(@RequestHeader("X-Transcode-Secret") secret: String) =
        service.claimNext(secret)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.noContent().build()

    @PostMapping("/jobs/{jobId}/complete")
    fun completeJob(
        @RequestHeader("X-Transcode-Secret") secret: String,
        @PathVariable jobId: UUID,
        @Valid @RequestBody request: CompleteTranscodeRequest
    ) = service.completeJob(secret, jobId, request)

    @PostMapping("/jobs/{jobId}/fail")
    fun failJob(
        @RequestHeader("X-Transcode-Secret") secret: String,
        @PathVariable jobId: UUID,
        @RequestParam reason: String
    ) = service.failJob(secret, jobId, reason)

    @PostMapping("/reels/{reelId}/complete")
    fun complete(@RequestHeader("X-Transcode-Secret") secret: String, @PathVariable reelId: UUID, @Valid @RequestBody request: CompleteTranscodeRequest) =
        service.complete(secret, reelId, request)

    @PostMapping("/reels/{reelId}/fail")
    fun fail(@RequestHeader("X-Transcode-Secret") secret: String, @PathVariable reelId: UUID, @RequestParam reason: String) =
        service.fail(secret, reelId, reason)
}
