package com.groovsta.identity.service.impl

import com.groovsta.identity.dto.response.AccountResponse
import com.groovsta.identity.model.Account
import com.groovsta.identity.service.ProfileService
import org.springframework.stereotype.Component

@Component
class AccountResponseAssembler(
    private val profileService: ProfileService
) {
    fun assemble(account: Account) = AccountResponse(
        id = account.id,
        email = account.email,
        phoneNumber = account.phoneNumber,
        status = account.status,
        emailVerified = account.emailVerifiedAt != null,
        phoneVerified = account.phoneVerifiedAt != null,
        profile = profileService.getByAccountId(account.id)
    )
}
