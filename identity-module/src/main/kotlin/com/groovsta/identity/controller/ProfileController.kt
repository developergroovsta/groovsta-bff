package com.groovsta.identity.controller

import com.groovsta.identity.dto.request.UpdateProfileRequest
import com.groovsta.identity.security.AuthenticatedAccountResolver
import com.groovsta.identity.service.ProfileService
import com.groovsta.identity.service.SocialGraphService
import com.groovsta.identity.web.OpenApiConfiguration
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Profiles")
class ProfileController(
    private val profileService: ProfileService,
    private val socialGraphService: SocialGraphService,
    private val authenticatedAccountResolver: AuthenticatedAccountResolver
) {

    @GetMapping("/me")
    @Operation(summary = "Get current profile", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun currentProfile(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?
    ) =
        profileService.getByAccountId(authenticatedAccountResolver.resolve(authorization))

    @PatchMapping("/me")
    @Operation(
        summary = "Update current profile",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun updateProfile(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: UpdateProfileRequest
    ) = profileService.update(authenticatedAccountResolver.resolve(authorization), request)

    @GetMapping("/profiles/{handle}")
    @Operation(summary = "Get profile by handle")
    fun profile(@PathVariable handle: String) = profileService.getByHandle(handle)

    @PostMapping("/profiles/{handle}/follow")
    @Operation(summary = "Follow a profile", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun follow(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable handle: String
    ) = socialGraphService.follow(authenticatedAccountResolver.resolve(authorization), handle)

    @DeleteMapping("/profiles/{handle}/follow")
    @Operation(summary = "Unfollow a profile", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun unfollow(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable handle: String
    ) = socialGraphService.unfollow(authenticatedAccountResolver.resolve(authorization), handle)
}
