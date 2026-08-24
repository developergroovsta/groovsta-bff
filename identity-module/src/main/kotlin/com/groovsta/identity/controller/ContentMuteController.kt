package com.groovsta.identity.controller

import com.groovsta.identity.model.ContentMuteType
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.service.ContentMuteService
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/mutes")
@Tag(name = "Content Mutes")
class ContentMuteController(
    private val service: ContentMuteService,
    private val resolver: AuthenticatedAccountResolver
) {
    @PutMapping("/{handle}/{contentType}")
    @Operation(
        summary = "Mute a creator's posts, reels, or stories",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun mute(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable handle: String,
        @PathVariable contentType: ContentMuteType
    ) = service.mute(resolver.resolve(auth), handle, contentType)

    @DeleteMapping("/{handle}/{contentType}")
    @Operation(
        summary = "Unmute a creator's posts, reels, or stories",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun unmute(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable handle: String,
        @PathVariable contentType: ContentMuteType
    ) = service.unmute(resolver.resolve(auth), handle, contentType)
}
