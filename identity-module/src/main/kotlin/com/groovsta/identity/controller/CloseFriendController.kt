package com.groovsta.identity.controller

import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.service.CloseFriendService
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me/close-friends")
@Tag(name = "Close Friends")
class CloseFriendController(
    private val service: CloseFriendService,
    private val resolver: AuthenticatedAccountResolver
) {
    @GetMapping
    @Operation(
        summary = "List the current account's close friends",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun list(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?
    ) = service.list(resolver.resolve(auth))

    @PutMapping("/{handle}")
    @Operation(
        summary = "Add a profile to close friends",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun add(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable handle: String
    ) = service.add(resolver.resolve(auth), handle)

    @DeleteMapping("/{handle}")
    @Operation(
        summary = "Remove a profile from close friends",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun remove(
        @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String?,
        @PathVariable handle: String
    ): ResponseEntity<Void> {
        service.remove(resolver.resolve(auth), handle)
        return ResponseEntity.noContent().build()
    }
}
