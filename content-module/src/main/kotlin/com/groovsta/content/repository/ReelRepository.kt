package com.groovsta.content.repository

import com.groovsta.content.model.Reel
import com.groovsta.content.model.ContentStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ReelRepository : JpaRepository<Reel, UUID> {
    fun findByStatusAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(status: ContentStatus, before: Instant, pageable: Pageable): List<Reel>
    fun findByStatusAndOwnerAccountIdInAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(status: ContentStatus, owners: Collection<UUID>, before: Instant, pageable: Pageable): List<Reel>

    @Query(
        """
        select reel from Reel reel, Profile profile
        where profile.accountId = reel.ownerAccountId
          and profile.isPublic = true
          and reel.status = :status
          and reel.publishedAt < :before
        order by reel.publishedAt desc, reel.id desc
        """
    )
    fun findPublicReels(
        @Param("status") status: ContentStatus,
        @Param("before") before: Instant,
        pageable: Pageable
    ): List<Reel>

    @Query(
        """
        select reel from Reel reel, Profile profile
        where profile.accountId = reel.ownerAccountId
          and profile.isPublic = true
          and reel.status = :status
          and reel.ownerAccountId not in :excludedOwnerIds
          and reel.publishedAt < :before
        order by reel.publishedAt desc, reel.id desc
        """
    )
    fun findPublicReelsExcludingOwners(
        @Param("status") status: ContentStatus,
        @Param("excludedOwnerIds") excludedOwnerIds: Collection<UUID>,
        @Param("before") before: Instant,
        pageable: Pageable
    ): List<Reel>
}
