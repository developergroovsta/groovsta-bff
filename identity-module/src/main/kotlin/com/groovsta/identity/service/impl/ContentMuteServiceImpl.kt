package com.groovsta.identity.service.impl

import com.groovsta.identity.dto.response.ContentMuteResponse
import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.exception.IdentityNotFoundException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.model.ContentMute
import com.groovsta.identity.model.ContentMuteType
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.ContentMuteRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.ContentMuteService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale
import java.util.UUID

@Service
class ContentMuteServiceImpl(
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val repository: ContentMuteRepository
) : ContentMuteService {

    @Transactional
    override fun mute(
        accountId: UUID,
        targetHandle: String,
        contentType: ContentMuteType
    ): ContentMuteResponse {
        requireActive(accountId)
        val target = target(targetHandle)
        if (target.accountId == accountId) {
            throw IdentityConflictException("An account cannot mute its own content")
        }
        requireActive(target.accountId)
        if (!repository.existsByAccountIdAndMutedAccountIdAndContentType(accountId, target.accountId, contentType)) {
            repository.save(
                ContentMute(accountId = accountId, mutedAccountId = target.accountId, contentType = contentType)
            )
        }
        return ContentMuteResponse(target.accountId, target.handle, contentType, muted = true)
    }

    @Transactional
    override fun unmute(
        accountId: UUID,
        targetHandle: String,
        contentType: ContentMuteType
    ): ContentMuteResponse {
        requireActive(accountId)
        val target = target(targetHandle)
        repository.deleteByAccountIdAndMutedAccountIdAndContentType(accountId, target.accountId, contentType)
        return ContentMuteResponse(target.accountId, target.handle, contentType, muted = false)
    }

    @Transactional(readOnly = true)
    override fun mutedAccountIds(accountId: UUID?, contentType: ContentMuteType): Set<UUID> =
        accountId?.let { repository.findMutedAccountIds(it, contentType) }.orEmpty()

    private fun target(handle: String) =
        profileRepository.findByHandle(handle.trim().lowercase(Locale.ROOT)).orElseThrow {
            IdentityNotFoundException("Profile was not found")
        }

    private fun requireActive(accountId: UUID) {
        val account = accountRepository.findById(accountId).orElseThrow {
            IdentityNotFoundException("Account was not found")
        }
        if (account.status != AccountStatus.ACTIVE) {
            throw IdentityConflictException("Account must be active to manage content mutes")
        }
    }
}
