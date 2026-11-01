package com.groovsta.identity.controller

import com.groovsta.identity.dto.request.CompleteAvatarUploadRequest
import com.groovsta.identity.dto.request.CreateAvatarUploadRequest
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
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

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
    ) = personalizedResponse(
        profileService.getByAccountId(authenticatedAccountResolver.resolve(authorization))
    )

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

    @PostMapping("/me/avatar/uploads")
    @Operation(summary = "Create an S3 avatar upload URL", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun createAvatarUpload(
        @Parameter(hidden = true) @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: CreateAvatarUploadRequest
    ) = profileService.createAvatarUpload(authenticatedAccountResolver.resolve(authorization), request)

    @PostMapping("/me/avatar/uploads/complete")
    @Operation(summary = "Attach a completed S3 avatar upload", security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)])
    fun completeAvatarUpload(
        @Parameter(hidden = true) @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @Valid @RequestBody request: CompleteAvatarUploadRequest
    ) = profileService.completeAvatarUpload(authenticatedAccountResolver.resolve(authorization), request)

    @GetMapping("/profiles/{handle}")
    @Operation(
        summary = "Get profile by handle",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun profile(
        @PathVariable handle: String,
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String? = null
    ) = personalizedResponse(
        profileService.getByHandle(handle, optionalViewerAccountId(authorization))
    )

    @GetMapping("/profiles/{handle}/followers")
    @Operation(
        summary = "List a profile's followers",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun followers(
        @PathVariable handle: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String? = null
    ) = personalizedResponse(
        socialGraphService.followers(handle, cursor, limit, optionalViewerAccountId(authorization))
    )

    @GetMapping("/profiles/{handle}/following")
    @Operation(
        summary = "List profiles followed by this profile",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun following(
        @PathVariable handle: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String? = null
    ) = personalizedResponse(
        socialGraphService.following(handle, cursor, limit, optionalViewerAccountId(authorization))
    )

    @GetMapping("/profiles/search")
    @Operation(
        summary = "Search active profiles by handle text",
        security = [SecurityRequirement(name = ""), SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun searchProfiles(
        @Parameter(description = "Case-insensitive text contained anywhere in the handle", example = "ing")
        @RequestParam q: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "10") limit: Int,
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String? = null
    ) = personalizedResponse(
        profileService.searchByHandleText(q, cursor, limit, optionalViewerAccountId(authorization))
    )

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

    @PostMapping("/follow-requests/{requestId}/accept")
    @Operation(
        summary = "Accept a private-profile follow request",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun acceptFollowRequest(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable requestId: UUID
    ) = socialGraphService.acceptFollowRequest(authenticatedAccountResolver.resolve(authorization), requestId)

    @DeleteMapping("/follow-requests/{requestId}")
    @Operation(
        summary = "Reject a private-profile follow request",
        security = [SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)]
    )
    fun rejectFollowRequest(
        @Parameter(hidden = true)
        @RequestHeader(name = HttpHeaders.AUTHORIZATION) authorization: String?,
        @PathVariable requestId: UUID
    ): ResponseEntity<Void> {
        socialGraphService.rejectFollowRequest(authenticatedAccountResolver.resolve(authorization), requestId)
        return ResponseEntity.noContent().build()
    }

    private fun optionalViewerAccountId(authorization: String?): UUID? =
        authorization?.let(authenticatedAccountResolver::resolve)

    private fun <T> personalizedResponse(body: T): ResponseEntity<T> = ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .header(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION)
        .body(body)
}
