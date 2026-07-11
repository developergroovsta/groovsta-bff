package com.groovsta.identity.dto.response

import com.groovsta.identity.model.AccountStatus
import java.util.UUID

data class AccountResponse(
    val id: UUID,
    val email: String?,
    val phoneNumber: String?,
    val status: AccountStatus,
    val emailVerified: Boolean,
    val phoneVerified: Boolean,
    val profile: ProfileResponse
)
