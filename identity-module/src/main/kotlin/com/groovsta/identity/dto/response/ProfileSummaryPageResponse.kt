package com.groovsta.identity.dto.response

data class ProfileSummaryPageResponse(
    val items: List<ProfileSummaryResponse>,
    val nextCursor: String?
)
