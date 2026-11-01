package com.groovsta.content.controller

import com.groovsta.content.dto.LocationContentType
import com.groovsta.content.dto.ResolveLocationRequest
import com.groovsta.content.service.LocationService
import com.groovsta.content.service.StoryService
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/locations")
@Tag(name = "Locations")
class LocationController(
    private val locationService: LocationService,
    private val storyService: StoryService,
    private val resolver: AuthenticatedAccountResolver
) {
    @GetMapping("/search")
    @Operation(
        summary = "Search locations for post, reel, or story tagging",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun search(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String,
        @RequestParam
        @Size(min = 1, max = 120)
        q: String,
        @RequestParam(required = false)
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        lat: Double?,
        @RequestParam(required = false)
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        lng: Double?,
        @RequestParam(defaultValue = "20")
        limit: Int
    ) = privateResponse(locationService.search(resolver.resolve(auth), q, lat, lng, limit))

    @PostMapping("/resolve")
    @Operation(
        summary = "Resolve a selected provider place into a stable Groovsta location",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun resolve(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String,
        @Valid @RequestBody request: ResolveLocationRequest
    ) = privateResponse(locationService.resolve(resolver.resolve(auth), request))

    @GetMapping("/reverse")
    @Operation(
        summary = "Resolve the current GPS coordinate into a Groovsta location",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun reverse(
        @Parameter(hidden = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) auth: String,
        @RequestParam
        @DecimalMin("-90.0")
        @DecimalMax("90.0")
        lat: Double,
        @RequestParam
        @DecimalMin("-180.0")
        @DecimalMax("180.0")
        lng: Double
    ) = privateResponse(locationService.reverse(resolver.resolve(auth), lat, lng))

    @GetMapping("/{locationId}")
    @Operation(summary = "Get location details")
    fun get(@PathVariable locationId: UUID) = locationService.get(locationId)

    @GetMapping("/{locationId}/content")
    @Operation(
        summary = "Get visible posts and reels tagged to a location",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun content(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable locationId: UUID,
        @RequestParam(defaultValue = "ALL") type: LocationContentType,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = privateResponse(locationService.content(optionalAccountId(auth), locationId, type, cursor, limit))

    @GetMapping("/{locationId}/stories")
    @Operation(
        summary = "Get active stories tagged to a location",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun stories(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) auth: String?,
        @PathVariable locationId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ) = privateResponse(storyService.visibleByLocation(optionalAccountId(auth), locationId, cursor, limit))

    private fun optionalAccountId(auth: String?) = auth?.takeIf(String::isNotBlank)?.let(resolver::resolve)

    private fun <T> privateResponse(body: T): ResponseEntity<T> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
        .body(body)
}
