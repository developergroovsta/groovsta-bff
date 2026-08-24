package com.groovsta.identity.repository

import com.groovsta.identity.model.ContentMute
import com.groovsta.identity.model.ContentMuteType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ContentMuteRepository : JpaRepository<ContentMute, UUID> {
    fun existsByAccountIdAndMutedAccountIdAndContentType(
        accountId: UUID,
        mutedAccountId: UUID,
        contentType: ContentMuteType
    ): Boolean

    fun deleteByAccountIdAndMutedAccountIdAndContentType(
        accountId: UUID,
        mutedAccountId: UUID,
        contentType: ContentMuteType
    ): Long

    @Query(
        """
        select mute.mutedAccountId
        from ContentMute mute
        where mute.accountId = :accountId and mute.contentType = :contentType
        """
    )
    fun findMutedAccountIds(
        @Param("accountId") accountId: UUID,
        @Param("contentType") contentType: ContentMuteType
    ): Set<UUID>

    @Query(
        """
        select mute.mutedAccountId as mutedAccountId, mute.contentType as contentType
        from ContentMute mute
        where mute.accountId = :accountId and mute.mutedAccountId in :targetAccountIds
        """
    )
    fun findMuteEdges(
        @Param("accountId") accountId: UUID,
        @Param("targetAccountIds") targetAccountIds: Collection<UUID>
    ): List<ContentMuteEdge>
}

interface ContentMuteEdge {
    val mutedAccountId: UUID
    val contentType: ContentMuteType
}
