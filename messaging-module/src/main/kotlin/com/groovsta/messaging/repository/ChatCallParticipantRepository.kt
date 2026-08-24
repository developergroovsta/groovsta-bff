package com.groovsta.messaging.repository

import com.groovsta.messaging.model.CallParticipantStatus
import com.groovsta.messaging.model.CallStatus
import com.groovsta.messaging.model.ChatCallParticipant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ChatCallParticipantRepository : JpaRepository<ChatCallParticipant, UUID> {
    fun findByCallIdAndAccountId(callId: UUID, accountId: UUID): ChatCallParticipant?
    fun findByCallIdOrderByInvitedAtAscIdAsc(callId: UUID): List<ChatCallParticipant>
    fun findByCallIdIn(callIds: Collection<UUID>): List<ChatCallParticipant>

    @Query(
        """
        select distinct participant.accountId
        from ChatCallParticipant participant, ChatCall call
        where participant.callId = call.id
          and participant.accountId in :accountIds
          and participant.status in :participantStatuses
          and call.status in :callStatuses
        """
    )
    fun findBusyAccountIds(
        @Param("accountIds") accountIds: Collection<UUID>,
        @Param("participantStatuses") participantStatuses: Collection<CallParticipantStatus>,
        @Param("callStatuses") callStatuses: Collection<CallStatus>
    ): Set<UUID>

    @Query(
        value = """
        SELECT id
        FROM identity_accounts
        WHERE id IN (:accountIds)
        ORDER BY id
        FOR UPDATE
        """,
        nativeQuery = true
    )
    fun lockAccounts(@Param("accountIds") accountIds: Collection<UUID>): List<UUID>

    @Query(
        """
        select participant from ChatCallParticipant participant, ChatCall call
        where participant.callId = call.id
          and call.status = :callStatus
          and participant.status = :participantStatus
          and participant.lastHeartbeatAt < :staleBefore
        """
    )
    fun findStaleParticipants(
        @Param("callStatus") callStatus: CallStatus,
        @Param("participantStatus") participantStatus: CallParticipantStatus,
        @Param("staleBefore") staleBefore: Instant
    ): List<ChatCallParticipant>

    @Query(
        """
        select participant from ChatCallParticipant participant, ChatCall call
        where participant.callId = call.id
          and call.status = :callStatus
          and participant.status = :participantStatus
          and participant.invitedAt <= :invitedBefore
        """
    )
    fun findExpiredInvitations(
        @Param("callStatus") callStatus: CallStatus,
        @Param("participantStatus") participantStatus: CallParticipantStatus,
        @Param("invitedBefore") invitedBefore: Instant
    ): List<ChatCallParticipant>
}
