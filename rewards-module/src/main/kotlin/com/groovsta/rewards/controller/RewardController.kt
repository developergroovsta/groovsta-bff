package com.groovsta.rewards.controller

import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.rewards.dto.ClaimRewardRequest
import com.groovsta.rewards.dto.RecordReelScrollRequest
import com.groovsta.rewards.service.RewardService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rewards")
@Tag(name = "Rewards")
class RewardController(
    private val service: RewardService,
    private val resolver: AuthenticatedAccountResolver
) {
    @GetMapping("/summary")
    @Operation(summary = "Get reward wallet, claimable coins, pending coins, and recent achievements")
    fun summary(@RequestHeader(HttpHeaders.AUTHORIZATION) auth: String) =
        service.summary(resolver.resolve(auth))

    @GetMapping("/achievements")
    @Operation(summary = "Get paginated reward achievements")
    fun achievements(
        @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "25") limit: Int
    ) = service.achievements(resolver.resolve(auth), cursor, limit)

    @GetMapping("/progress")
    @Operation(summary = "Get current progress toward reward milestones")
    fun progress(@RequestHeader(HttpHeaders.AUTHORIZATION) auth: String) =
        service.progress(resolver.resolve(auth))

    @PostMapping("/claims")
    @Operation(summary = "Claim one claimable achievement or all claimable achievements")
    fun claim(
        @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String,
        @Valid @RequestBody request: ClaimRewardRequest
    ) = service.claim(resolver.resolve(auth), request)

    @PostMapping("/reel-scrolls")
    @Operation(summary = "Record a unique reel scroll for the authenticated user")
    fun recordReelScroll(
        @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String,
        @Valid @RequestBody request: RecordReelScrollRequest
    ) = service.recordReelScroll(resolver.resolve(auth), request)
}
