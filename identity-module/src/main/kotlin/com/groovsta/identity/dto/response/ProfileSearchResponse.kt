package com.groovsta.identity.dto.response

data class ProfileSearchResponse(
    val items: List<ProfileSummaryResponse>,
    val nextCursor: String?
)
