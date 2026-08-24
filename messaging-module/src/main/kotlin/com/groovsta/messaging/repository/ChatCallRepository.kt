package com.groovsta.messaging.repository

import com.groovsta.messaging.model.CallStatus
import com.groovsta.messaging.model.CallParticipantStatus
import com.groovsta.messaging.model.ChatCall
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChatCallRepository : JpaRepository<ChatCall, UUID> {
    fun findByInitiatorAccountIdAndClientCallId(initiatorAccountId: UUID, clientCallId: UUID): ChatCall?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select call from ChatCall call where call.id = :id")
    fun findLockedById(@Param("id") id: UUID): ChatCall?

    @Query(
        """
        select call from ChatCall call
        where exists (
            select participant.id from ChatCallParticipant participant
            where participant.callId = call.id and participant.accountId = :accountId
        )
        order by call.createdAt desc, call.id desc
        """
    )
    fun findHistory(@Param("accountId") accountId: UUID, pageable: Pageable): List<ChatCall>

    @Query(
        """
        select call from ChatCall call
        where exists (
            select participant.id from ChatCallParticipant participant
            where participant.callId = call.id and participant.accountId = :accountId
        )
          and (
              call.createdAt < :createdAt
              or (call.createdAt = :createdAt and call.id < :id)
          )
        order by call.createdAt desc, call.id desc
        """
    )
    fun findHistoryAfter(
        @Param("accountId") accountId: UUID,
        @Param("createdAt") createdAt: Instant,
        @Param("id") id: UUID,
        pageable: Pageable
    ): List<ChatCall>

    @Query(
        """
        select distinct call from ChatCall call, ChatCallParticipant participant
        where participant.callId = call.id
          and participant.accountId = :accountId
          and participant.status in :participantStatuses
          and call.status in :statuses
        order by call.createdAt desc
        """
    )
    fun findByMemberAndStatusIn(
        @Param("accountId") accountId: UUID,
        @Param("participantStatuses") participantStatuses: Collection<CallParticipantStatus>,
        @Param("statuses") statuses: Collection<CallStatus>
    ): List<ChatCall>

    fun findByStatusAndRingExpiresAtLessThanEqual(status: CallStatus, now: Instant): List<ChatCall>
}
