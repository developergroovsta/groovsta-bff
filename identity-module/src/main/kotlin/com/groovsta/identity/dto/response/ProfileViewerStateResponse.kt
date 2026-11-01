package com.groovsta.identity.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Profile state personalized for the authenticated viewer; null for anonymous requests.")
data class ProfileViewerStateResponse(
    val relationship: ProfileRelationship,
    val followsViewer: Boolean,
    val canViewContent: Boolean,
    val postsMuted: Boolean,
    val reelsMuted: Boolean,
    val storiesMuted: Boolean
)
