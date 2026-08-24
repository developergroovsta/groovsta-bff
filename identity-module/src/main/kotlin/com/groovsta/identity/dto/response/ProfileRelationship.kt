package com.groovsta.identity.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "Authenticated viewer's relationship to a profile."
)
enum class ProfileRelationship {
    SELF,
    NONE,
    FOLLOWING,
    REQUESTED
}
