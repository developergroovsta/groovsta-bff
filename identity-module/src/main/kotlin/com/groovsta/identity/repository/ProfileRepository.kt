package com.groovsta.identity.repository

import com.groovsta.identity.model.Profile
import com.groovsta.identity.model.AccountStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface ProfileRepository : JpaRepository<Profile, UUID> {
    fun findByAccountId(accountId: UUID): Optional<Profile>
    fun findByAccountIdIn(accountIds: Collection<UUID>): List<Profile>
    fun findByHandle(handle: String): Optional<Profile>
    fun existsByHandle(handle: String): Boolean

    @Query(
        """
        select profile from Profile profile, Account account
        where account.id = profile.accountId
          and account.status = :status
          and profile.accountId in :accountIds
        """
    )
    fun findMentionableByAccountIdIn(
        @Param("accountIds") accountIds: Collection<UUID>,
        @Param("status") status: AccountStatus
    ): List<Profile>

    @Query(
        """
        select profile from Profile profile, Account account
        where account.id = profile.accountId
          and account.status = :status
          and profile.handle like :handlePattern escape '!'
          and profile.handle > :afterHandle
        order by profile.handle asc
        """
    )
    fun searchByHandleText(
        @Param("handlePattern") handlePattern: String,
        @Param("afterHandle") afterHandle: String,
        @Param("status") status: AccountStatus,
        pageable: Pageable
    ): List<Profile>
}
