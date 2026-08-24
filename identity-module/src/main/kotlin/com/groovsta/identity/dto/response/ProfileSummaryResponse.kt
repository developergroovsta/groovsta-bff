package com.groovsta.identity.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class ProfileSummaryResponse(
    val accountId: UUID,
    val handle: String,
    val displayName: String,
    val avatarUrl: String?,
    @field:Schema(
        description = "Viewer-specific state when an optional-auth endpoint receives a bearer token; otherwise null.",
        nullable = true
    )
    val viewerState: ProfileViewerStateResponse? = null
)
