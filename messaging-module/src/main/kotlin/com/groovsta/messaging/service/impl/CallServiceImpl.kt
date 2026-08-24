package com.groovsta.messaging.service.impl

import com.groovsta.identity.exception.IdentityConflictException
import com.groovsta.identity.model.AccountStatus
import com.groovsta.identity.repository.AccountRepository
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.SocialGraphService
import com.groovsta.messaging.config.CallProperties
import com.groovsta.messaging.dto.AddCallParticipantsRequest
import com.groovsta.messaging.dto.CallEventResponse
import com.groovsta.messaging.dto.CallEventType
import com.groovsta.messaging.dto.CallHeartbeatRequest
import com.groovsta.messaging.dto.CallMediaStateRequest
import com.groovsta.messaging.dto.CallPageResponse
import com.groovsta.messaging.dto.CallResponse
import com.groovsta.messaging.dto.CallSignalRequest
import com.groovsta.messaging.dto.CallSignalResponse
import com.groovsta.messaging.dto.CallSignalType
import com.groovsta.messaging.dto.CreateCallRequest
import com.groovsta.messaging.dto.IceConfigurationResponse
import com.groovsta.messaging.dto.IceServerResponse
import com.groovsta.messaging.model.CallEndReason
import com.groovsta.messaging.model.CallParticipantRole
import com.groovsta.messaging.model.CallParticipantStatus
import com.groovsta.messaging.model.CallStatus
import com.groovsta.messaging.model.CallType
import com.groovsta.messaging.model.ChatCall
import com.groovsta.messaging.model.ChatCallParticipant
import com.groovsta.messaging.repository.ChatCallParticipantRepository
import com.groovsta.messaging.repository.ChatCallRepository
import com.groovsta.messaging.service.CallCommittedEvent
import com.groovsta.messaging.service.CallRealtimeEventListener
import com.groovsta.messaging.service.CallResponseAssembler
import com.groovsta.messaging.service.CallService
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import com.groovsta.shared.notification.PushMessageCommand
import com.groovsta.shared.notification.PushMessagePublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.Duration
import java.util.Base64
import java.util.UUID

@Service
class CallServiceImpl(
    private val callRepository: ChatCallRepository,
    private val participantRepository: ChatCallParticipantRepository,
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository,
    private val socialGraphService: SocialGraphService,
    private val responseAssembler: CallResponseAssembler,
    private val properties: CallProperties,
    private val eventPublisher: ApplicationEventPublisher,
    private val messagingTemplate: SimpMessagingTemplate,
    private val notificationPublisher: NotificationPublisher,
    private val pushMessagePublisher: PushMessagePublisher
) : CallService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun create(accountId: UUID, request: CreateCallRequest): CallResponse {
        log.info(
            "call.create request initiator={} clientCallId={} type={} invitedCount={}",
            accountId,
            request.clientCallId,
            request.type,
            request.participantAccountIds.size
        )
        requireActiveAccount(accountId)
        validateConfiguredLimit()
        existingCall(accountId, request)?.let { return it }
        if (request.participantAccountIds.toSet().size != request.participantAccountIds.size) {
            throw IllegalArgumentException("Call participantAccountIds must not contain duplicates")
        }
        if (accountId in request.participantAccountIds) {
            throw IdentityConflictException("The initiator must not also be an invited participant")
        }
        val allAccountIds = linkedSetOf(accountId).apply { addAll(request.participantAccountIds) }
        if (allAccountIds.size !in 2..properties.maxParticipants) {
            throw IllegalArgumentException(
                "A call must have 2 to ${properties.maxParticipants} total participants"
            )
        }

        lockAndValidateAccounts(allAccountIds, accountId)
        existingCall(accountId, request)?.let { return it }
        requireNotBusy(allAccountIds)

        val now = Instant.now()
        val call = callRepository.saveAndFlush(
            ChatCall(
                initiatorAccountId = accountId,
                clientCallId = request.clientCallId,
                type = request.type,
                status = CallStatus.RINGING,
                maxParticipants = properties.maxParticipants,
                ringExpiresAt = now.plus(properties.ringTimeout)
            )
        )
        val participants = allAccountIds.map { participantAccountId ->
            val initiator = participantAccountId == accountId
            ChatCallParticipant(
                callId = call.id,
                accountId = participantAccountId,
                initialParticipant = true,
                invitedByAccountId = accountId,
                role = if (initiator) CallParticipantRole.HOST else CallParticipantRole.MEMBER,
                status = if (initiator) CallParticipantStatus.JOINED else CallParticipantStatus.INVITED,
                invitedAt = now,
                joinedAt = now.takeIf { initiator },
                lastHeartbeatAt = now.takeIf { initiator },
                cameraEnabled = initiator && request.type == CallType.VIDEO
            )
        }
        participantRepository.saveAllAndFlush(participants)
        publishCall(
            call = call,
            eventType = CallEventType.CALL_CREATED,
            eventTypeByAccountId = request.participantAccountIds.associateWith { CallEventType.INCOMING_CALL },
            includeAllMembers = true
        )
        log.info(
            "call.create ready callId={} status={} participantCount={}",
            call.id,
            call.status,
            participants.size
        )
        return responseAssembler.response(call, accountId)
    }

    @Transactional(readOnly = true)
    override fun get(accountId: UUID, callId: UUID): CallResponse {
        requireActiveAccount(accountId)
        val call = participantCall(callId, accountId)
        return responseAssembler.response(call, accountId)
    }

    @Transactional(readOnly = true)
    override fun history(accountId: UUID, cursor: String?, limit: Int): CallPageResponse {
        requireActiveAccount(accountId)
        val pageSize = limit.coerceIn(1, 50)
        val decoded = cursor?.let(CallCursor::decode)
        val fetched = if (decoded == null) {
            callRepository.findHistory(accountId, PageRequest.of(0, pageSize + 1))
        } else {
            callRepository.findHistoryAfter(
                accountId,
                decoded.createdAt,
                decoded.id,
                PageRequest.of(0, pageSize + 1)
            )
        }
        val page = fetched.take(pageSize)
        val responses = responseAssembler.responses(page, accountId)
        return CallPageResponse(
            items = page.mapNotNull { responses[it.id] },
            nextCursor = page.lastOrNull()
                ?.takeIf { fetched.size > pageSize }
                ?.let(CallCursor::from)
                ?.encode()
        )
    }

    @Transactional(readOnly = true)
    override fun active(accountId: UUID): List<CallResponse> {
        requireActiveAccount(accountId)
        val calls = callRepository.findByMemberAndStatusIn(
            accountId,
            ACTIVE_PARTICIPANT_STATUSES,
            ONGOING_CALL_STATUSES
        )
        val responses = responseAssembler.responses(calls, accountId)
        return calls.mapNotNull { responses[it.id] }
    }

    @Transactional
    override fun accept(accountId: UUID, callId: UUID): CallResponse {
        log.info("call.accept request accountId={} callId={}", accountId, callId)
        requireActiveAccount(accountId)
        val call = lockedParticipantCall(callId, accountId)
        val participant = requiredParticipant(call.id, accountId)
        if (participant.status == CallParticipantStatus.JOINED) {
            return responseAssembler.response(call, accountId)
        }
        if (call.status == CallStatus.ENDED || participant.status != CallParticipantStatus.INVITED) {
            throw IdentityConflictException("This call invitation can no longer be accepted")
        }
        if (!Instant.now().isBefore(participant.invitedAt.plus(properties.ringTimeout))) {
            throw ResponseStatusException(HttpStatus.GONE, "This call invitation has expired")
        }

        val now = Instant.now()
        participant.status = CallParticipantStatus.JOINED
        participant.joinedAt = now
        participant.leftAt = null
        participant.lastHeartbeatAt = now
        participant.cameraEnabled = call.type == CallType.VIDEO
        if (call.status == CallStatus.RINGING) {
            call.status = CallStatus.ACTIVE
            call.startedAt = now
        }
        participantRepository.save(participant)
        callRepository.saveAndFlush(call)
        publishCall(call, CallEventType.PARTICIPANT_JOINED)
        log.info(
            "call.accept applied accountId={} callId={} callStatus={} participantStatus={}",
            accountId,
            callId,
            call.status,
            participant.status
        )
        return responseAssembler.response(call, accountId)
    }

    @Transactional
    override fun decline(accountId: UUID, callId: UUID): CallResponse {
        requireActiveAccount(accountId)
        val call = lockedParticipantCall(callId, accountId)
        val participant = requiredParticipant(call.id, accountId)
        if (participant.status == CallParticipantStatus.DECLINED) {
            return responseAssembler.response(call, accountId)
        }
        if (call.status == CallStatus.ENDED || participant.status != CallParticipantStatus.INVITED) {
            throw IdentityConflictException("Only a pending call invitation can be declined")
        }

        participant.status = CallParticipantStatus.DECLINED
        participant.leftAt = Instant.now()
        participantRepository.save(participant)
        val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        if (call.status == CallStatus.RINGING && participants.none { it.status == CallParticipantStatus.INVITED }) {
            endCall(call, participants, CallEndReason.DECLINED, accountId)
        } else {
            callRepository.saveAndFlush(call)
        }
        publishCall(
            call,
            CallEventType.PARTICIPANT_DECLINED,
            additionalRecipients = setOf(accountId),
            includeAllMembers = call.status == CallStatus.ENDED
        )
        return responseAssembler.response(call, accountId)
    }

    @Transactional
    override fun cancel(accountId: UUID, callId: UUID): CallResponse {
        requireActiveAccount(accountId)
        val call = lockedParticipantCall(callId, accountId)
        if (call.status == CallStatus.ENDED &&
            call.endReason == CallEndReason.CANCELLED &&
            call.endedByAccountId == accountId
        ) {
            return responseAssembler.response(call, accountId)
        }
        if (call.initiatorAccountId != accountId || call.status != CallStatus.RINGING) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the initiator can cancel a ringing call")
        }
        val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        endCall(call, participants, CallEndReason.CANCELLED, accountId)
        publishCall(call, CallEventType.CALL_ENDED, includeAllMembers = true)
        return responseAssembler.response(call, accountId)
    }

    @Transactional
    override fun leave(accountId: UUID, callId: UUID): CallResponse {
        requireActiveAccount(accountId)
        val call = lockedParticipantCall(callId, accountId)
        val participant = requiredParticipant(call.id, accountId)
        if (call.status == CallStatus.ENDED && participant.status == CallParticipantStatus.LEFT) {
            return responseAssembler.response(call, accountId)
        }
        if (call.status == CallStatus.RINGING && call.initiatorAccountId == accountId) {
            val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
            endCall(call, participants, CallEndReason.CANCELLED, accountId)
            publishCall(call, CallEventType.CALL_ENDED, includeAllMembers = true)
            return responseAssembler.response(call, accountId)
        }
        if (call.status != CallStatus.ACTIVE || participant.status != CallParticipantStatus.JOINED) {
            throw IdentityConflictException("Only a joined participant can leave an active call")
        }

        participant.status = CallParticipantStatus.LEFT
        participant.leftAt = Instant.now()
        participant.cameraEnabled = false
        participantRepository.save(participant)
        val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        finishOrPromoteHost(call, participants, participant, CallEndReason.COMPLETED)
        publishCall(
            call,
            if (call.status == CallStatus.ENDED) CallEventType.CALL_ENDED else CallEventType.PARTICIPANT_LEFT,
            additionalRecipients = setOf(accountId),
            includeAllMembers = call.status == CallStatus.ENDED
        )
        return responseAssembler.response(call, accountId)
    }

    @Transactional
    override fun end(accountId: UUID, callId: UUID): CallResponse {
        requireActiveAccount(accountId)
        val call = lockedParticipantCall(callId, accountId)
        val participant = requiredParticipant(call.id, accountId)
        if (call.status == CallStatus.ENDED && call.endedByAccountId == accountId) {
            return responseAssembler.response(call, accountId)
        }
        if (participant.role != CallParticipantRole.HOST || participant.status != CallParticipantStatus.JOINED) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the current call host can end the call")
        }
        val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        endCall(
            call,
            participants,
            if (call.status == CallStatus.RINGING) CallEndReason.CANCELLED else CallEndReason.COMPLETED,
            accountId
        )
        publishCall(call, CallEventType.CALL_ENDED, includeAllMembers = true)
        return responseAssembler.response(call, accountId)
    }

    @Transactional
    override fun invite(
        accountId: UUID,
        callId: UUID,
        request: AddCallParticipantsRequest
    ): CallResponse {
        log.info(
            "call.invite request hostAccountId={} callId={} invitedCount={}",
            accountId,
            callId,
            request.participantAccountIds.size
        )
        requireActiveAccount(accountId)
        val call = lockedParticipantCall(callId, accountId)
        val host = requiredParticipant(call.id, accountId)
        if (call.status == CallStatus.ENDED ||
            host.role != CallParticipantRole.HOST ||
            host.status != CallParticipantStatus.JOINED
        ) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the active call host can invite participants")
        }
        if (accountId in request.participantAccountIds) {
            throw IdentityConflictException("The host is already in the call")
        }
        if (request.participantAccountIds.toSet().size != request.participantAccountIds.size) {
            throw IllegalArgumentException("Call participantAccountIds must not contain duplicates")
        }
        val currentParticipants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        val currentAccountIds = currentParticipants.mapTo(mutableSetOf(), ChatCallParticipant::accountId)
        val duplicateIds = request.participantAccountIds.intersect(currentAccountIds)
        if (duplicateIds.isNotEmpty()) {
            throw IdentityConflictException("One or more invited accounts already belong to this call")
        }
        if (currentParticipants.size + request.participantAccountIds.size > call.maxParticipants) {
            throw IdentityConflictException("This call supports at most ${call.maxParticipants} participants")
        }

        val allAccountIds = (currentAccountIds + request.participantAccountIds).toSet()
        lockAndValidateAccounts(allAccountIds, accountId)
        requireNotBusy(request.participantAccountIds)
        val now = Instant.now()
        participantRepository.saveAllAndFlush(
            request.participantAccountIds.map { invitedAccountId ->
                ChatCallParticipant(
                    callId = call.id,
                    accountId = invitedAccountId,
                    initialParticipant = false,
                    invitedByAccountId = accountId,
                    role = CallParticipantRole.MEMBER,
                    status = CallParticipantStatus.INVITED,
                    invitedAt = now
                )
            }
        )
        val invitationExpiry = now.plus(properties.ringTimeout)
        if (invitationExpiry.isAfter(call.ringExpiresAt)) call.ringExpiresAt = invitationExpiry
        callRepository.saveAndFlush(call)
        publishCall(
            call,
            CallEventType.PARTICIPANT_INVITED,
            eventTypeByAccountId = request.participantAccountIds.associateWith { CallEventType.INCOMING_CALL }
        )
        log.info(
            "call.invite applied callId={} invitedCount={} ringExpiresAt={}",
            call.id,
            request.participantAccountIds.size,
            call.ringExpiresAt
        )
        return responseAssembler.response(call, accountId)
    }

    @Transactional
    override fun updateMedia(
        accountId: UUID,
        callId: UUID,
        request: CallMediaStateRequest
    ): CallResponse {
        log.info(
            "call.media-state request accountId={} callId={} microphoneMuted={} cameraEnabled={}",
            accountId,
            callId,
            request.microphoneMuted,
            request.cameraEnabled
        )
        requireActiveAccount(accountId)
        val call = lockedParticipantCall(callId, accountId)
        val participant = requiredParticipant(call.id, accountId)
        if (call.status == CallStatus.ENDED || participant.status != CallParticipantStatus.JOINED) {
            throw IdentityConflictException("Only a joined participant can change call media state")
        }
        if (call.type == CallType.AUDIO && request.cameraEnabled) {
            throw IllegalArgumentException("Camera cannot be enabled in an audio call")
        }
        participant.microphoneMuted = request.microphoneMuted
        participant.cameraEnabled = request.cameraEnabled
        participant.lastHeartbeatAt = Instant.now()
        participantRepository.saveAndFlush(participant)
        publishCall(call, CallEventType.PARTICIPANT_MEDIA_CHANGED)
        log.info("call.media-state applied accountId={} callId={}", accountId, callId)
        return responseAssembler.response(call, accountId)
    }

    @Transactional
    override fun heartbeat(accountId: UUID, request: CallHeartbeatRequest) {
        log.debug("call.heartbeat accountId={} callId={}", accountId, request.callId)
        requireActiveAccount(accountId)
        val call = lockedParticipantCall(request.callId, accountId)
        val participant = requiredParticipant(call.id, accountId)
        if (call.status == CallStatus.ENDED || participant.status != CallParticipantStatus.JOINED) {
            throw IdentityConflictException("Only a joined participant can heartbeat an ongoing call")
        }
        participant.lastHeartbeatAt = Instant.now()
        participantRepository.save(participant)
    }

    @Transactional(readOnly = true)
    override fun relaySignal(accountId: UUID, request: CallSignalRequest) {
        log.info(
            "call.signal relay senderAccountId={} recipientAccountId={} callId={} type={} hasSdp={} hasCandidate={}",
            accountId,
            request.recipientAccountId,
            request.callId,
            request.type,
            !request.sdp.isNullOrBlank(),
            !request.candidate.isNullOrBlank()
        )
        requireActiveAccount(accountId)
        if (accountId == request.recipientAccountId) {
            throw IllegalArgumentException("A call signal cannot be sent to the same account")
        }
        validateSignalPayload(request)
        val call = participantCall(request.callId, accountId)
        if (call.status != CallStatus.ACTIVE) {
            throw IdentityConflictException("WebRTC signaling is allowed only for an active call")
        }
        val sender = requiredParticipant(call.id, accountId)
        val recipient = requiredParticipant(call.id, request.recipientAccountId)
        if (sender.status != CallParticipantStatus.JOINED || recipient.status != CallParticipantStatus.JOINED) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Both signaling accounts must be joined participants")
        }
        messagingTemplate.convertAndSendToUser(
            request.recipientAccountId.toString(),
            CallRealtimeEventListener.CALL_EVENTS_DESTINATION,
            CallEventResponse(
                type = CallEventType.SIGNAL,
                signal = CallSignalResponse(
                    callId = call.id,
                    senderAccountId = accountId,
                    recipientAccountId = request.recipientAccountId,
                    type = request.type,
                    sdp = request.sdp,
                    candidate = request.candidate,
                    sdpMid = request.sdpMid,
                    sdpMLineIndex = request.sdpMLineIndex
                )
            )
        )
        log.info(
            "call.signal delivered senderAccountId={} recipientAccountId={} callId={} destination={}",
            accountId,
            request.recipientAccountId,
            call.id,
            CallRealtimeEventListener.CALL_EVENTS_DESTINATION
        )
    }

    @Transactional(readOnly = true)
    override fun iceConfiguration(accountId: UUID): IceConfigurationResponse {
        log.info("call.ice-configuration request accountId={}", accountId)
        requireActiveAccount(accountId)
        validateConfiguredLimit()
        val stunUrls = properties.stunUrls.map(String::trim).filter(String::isNotEmpty)
        val turnUrls = properties.turnUrls.map(String::trim).filter(String::isNotEmpty)
        val servers = mutableListOf<IceServerResponse>()
        if (stunUrls.isNotEmpty()) servers += IceServerResponse(urls = stunUrls)
        if (turnUrls.isNotEmpty()) {
            val username = properties.turnUsername?.trim()?.takeIf(String::isNotEmpty)
            val credential = properties.turnCredential?.takeIf(String::isNotEmpty)
            check(username != null && credential != null) {
                "TURN username and credential are required when TURN URLs are configured"
            }
            servers += IceServerResponse(turnUrls, username, credential)
        }
        log.info(
            "call.ice-configuration response accountId={} stunCount={} turnCount={} turnHasUsername={} turnHasCredential={}",
            accountId,
            stunUrls.size,
            turnUrls.size,
            properties.turnUsername?.isNotBlank() == true,
            properties.turnCredential?.isNotBlank() == true
        )
        return IceConfigurationResponse(
            iceServers = servers,
            groupParticipantLimit = properties.maxParticipants,
            heartbeatIntervalSeconds = (properties.heartbeatTimeout.seconds / 3).coerceAtLeast(5)
        )
    }

    @Transactional
    override fun expireStaleCalls() {
        val now = Instant.now()
        log.debug("call.cleanup tick now={}", now)
        val invitationCutoff = now.minus(properties.ringTimeout)
        val expiredInvitations = (
            participantRepository.findExpiredInvitations(
                CallStatus.RINGING,
                CallParticipantStatus.INVITED,
                invitationCutoff
            ) + participantRepository.findExpiredInvitations(
                CallStatus.ACTIVE,
                CallParticipantStatus.INVITED,
                invitationCutoff
            )
        ).distinctBy(ChatCallParticipant::id)
        expiredInvitations.forEach { stale -> expireInvitation(stale.callId, stale.accountId, now) }

        participantRepository.findStaleParticipants(
            CallStatus.ACTIVE,
            CallParticipantStatus.JOINED,
            now.minus(properties.heartbeatTimeout)
        ).forEach { stale -> expireJoinedParticipant(stale.callId, stale.accountId, now) }

        callRepository.findByStatusAndRingExpiresAtLessThanEqual(CallStatus.RINGING, now)
            .forEach { staleCall -> expireEmptyRingingCall(staleCall.id, now) }
    }

    private fun expireInvitation(callId: UUID, accountId: UUID, now: Instant) {
        val call = callRepository.findLockedById(callId) ?: return
        if (call.status == CallStatus.ENDED) return
        val participant = participantRepository.findByCallIdAndAccountId(call.id, accountId) ?: return
        if (participant.status != CallParticipantStatus.INVITED ||
            participant.invitedAt.plus(properties.ringTimeout).isAfter(now)
        ) return

        participant.status = CallParticipantStatus.MISSED
        participant.leftAt = now
        participantRepository.save(participant)
        notifyMissedCall(call, participant)
        val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        if (call.status == CallStatus.RINGING && participants.none { it.status == CallParticipantStatus.INVITED }) {
            endCall(call, participants, CallEndReason.MISSED, null)
        } else {
            callRepository.saveAndFlush(call)
        }
        publishCall(
            call,
            CallEventType.PARTICIPANT_MISSED,
            additionalRecipients = setOf(accountId),
            includeAllMembers = call.status == CallStatus.ENDED
        )
    }

    private fun expireJoinedParticipant(callId: UUID, accountId: UUID, now: Instant) {
        val call = callRepository.findLockedById(callId) ?: return
        if (call.status != CallStatus.ACTIVE) return
        val participant = participantRepository.findByCallIdAndAccountId(call.id, accountId) ?: return
        val heartbeat = participant.lastHeartbeatAt ?: return
        if (participant.status != CallParticipantStatus.JOINED ||
            heartbeat.plus(properties.heartbeatTimeout).isAfter(now)
        ) return

        participant.status = CallParticipantStatus.LEFT
        participant.leftAt = now
        participant.cameraEnabled = false
        participantRepository.save(participant)
        val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        finishOrPromoteHost(call, participants, participant, CallEndReason.FAILED)
        publishCall(
            call,
            if (call.status == CallStatus.ENDED) CallEventType.CALL_ENDED else CallEventType.PARTICIPANT_LEFT,
            additionalRecipients = setOf(accountId),
            includeAllMembers = call.status == CallStatus.ENDED
        )
    }

    private fun expireEmptyRingingCall(callId: UUID, now: Instant) {
        val call = callRepository.findLockedById(callId) ?: return
        if (call.status != CallStatus.RINGING || call.ringExpiresAt.isAfter(now)) return
        val participants = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
        if (participants.any { it.status == CallParticipantStatus.INVITED }) return
        endCall(call, participants, CallEndReason.MISSED, null)
        publishCall(call, CallEventType.CALL_ENDED, includeAllMembers = true)
    }

    private fun existingCall(accountId: UUID, request: CreateCallRequest): CallResponse? {
        val existing = callRepository.findByInitiatorAccountIdAndClientCallId(accountId, request.clientCallId)
            ?: return null
        val participantIds = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(existing.id)
            .filter(ChatCallParticipant::initialParticipant)
            .mapTo(mutableSetOf(), ChatCallParticipant::accountId)
        val sameRequest = existing.type == request.type &&
            participantIds == request.participantAccountIds.toSet() + accountId
        if (!sameRequest) {
            throw IdentityConflictException("clientCallId was already used for a different call")
        }
        return responseAssembler.response(existing, accountId)
    }

    private fun lockAndValidateAccounts(accountIds: Set<UUID>, initiatorAccountId: UUID) {
        val lockedIds = participantRepository.lockAccounts(accountIds).toSet()
        if (lockedIds != accountIds) profileNotFound()
        val accounts = accountRepository.findAllById(accountIds).associateBy { it.id }
        if (accounts.size != accountIds.size || accounts.values.any { it.status != AccountStatus.ACTIVE }) {
            profileNotFound()
        }
        val profiles = profileRepository.findByAccountIdIn(accountIds).associateBy { it.accountId }
        if (profiles.size != accountIds.size) profileNotFound()

        val sourceTargetPairs = if (accountIds.size == 2) {
            listOf(initiatorAccountId to accountIds.first { it != initiatorAccountId })
        } else {
            accountIds.flatMap { sourceAccountId ->
                accountIds.filter { it != sourceAccountId }.map { targetAccountId ->
                    sourceAccountId to targetAccountId
                }
            }
        }
        sourceTargetPairs.forEach { (sourceAccountId, targetAccountId) ->
            val targetProfile = requireNotNull(profiles[targetAccountId])
            if (!targetProfile.isPublic &&
                !socialGraphService.isFollowing(sourceAccountId, targetAccountId)
            ) {
                throw ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    if (accountIds.size == 2) {
                        "Calling this private account is not allowed"
                    } else {
                        "Every participant must be allowed to contact every private account in a group call"
                    }
                )
            }
        }
    }

    private fun requireNotBusy(accountIds: Collection<UUID>) {
        if (accountIds.isEmpty()) return
        val busy = participantRepository.findBusyAccountIds(
            accountIds,
            ACTIVE_PARTICIPANT_STATUSES,
            ONGOING_CALL_STATUSES
        )
        if (busy.isNotEmpty()) {
            throw IdentityConflictException("One or more accounts are already in another call")
        }
    }

    private fun finishOrPromoteHost(
        call: ChatCall,
        participants: List<ChatCallParticipant>,
        departed: ChatCallParticipant,
        reasonWhenFinished: CallEndReason
    ) {
        val joined = participants.filter { it.status == CallParticipantStatus.JOINED }
        if (joined.size < 2) {
            endCall(call, participants, reasonWhenFinished, departed.accountId)
            return
        }
        if (departed.role == CallParticipantRole.HOST) {
            departed.role = CallParticipantRole.MEMBER
            joined.minWithOrNull(compareBy<ChatCallParticipant> { it.joinedAt }.thenBy { it.id })
                ?.role = CallParticipantRole.HOST
            participantRepository.saveAll(participants)
        }
        callRepository.saveAndFlush(call)
    }

    private fun endCall(
        call: ChatCall,
        participants: Collection<ChatCallParticipant>,
        reason: CallEndReason,
        endedByAccountId: UUID?
    ) {
        if (call.status == CallStatus.ENDED) return
        val now = Instant.now()
        call.status = CallStatus.ENDED
        call.endReason = reason
        call.endedAt = now
        call.endedByAccountId = endedByAccountId
        participants.forEach { participant ->
            when (participant.status) {
                CallParticipantStatus.JOINED -> {
                    participant.status = CallParticipantStatus.LEFT
                    participant.leftAt = participant.leftAt ?: now
                    participant.cameraEnabled = false
                }
                CallParticipantStatus.INVITED -> {
                    participant.status = if (reason == CallEndReason.MISSED) {
                        CallParticipantStatus.MISSED
                    } else {
                        CallParticipantStatus.CANCELLED
                    }
                    participant.leftAt = participant.leftAt ?: now
                }
                else -> Unit
            }
        }
        participantRepository.saveAll(participants)
        callRepository.saveAndFlush(call)
    }

    private fun publishCall(
        call: ChatCall,
        eventType: CallEventType,
        eventTypeByAccountId: Map<UUID, CallEventType> = emptyMap(),
        additionalRecipients: Set<UUID> = emptySet(),
        includeAllMembers: Boolean = false
    ) {
        val responses = responseAssembler.responsesForMembers(call)
        val participantStates = participantRepository.findByCallIdOrderByInvitedAtAscIdAsc(call.id)
            .associateBy(ChatCallParticipant::accountId)
        val recipientIds = if (includeAllMembers) {
            responses.keys
        } else {
            responses.keys.filterTo(mutableSetOf()) { accountId ->
                participantStates[accountId]?.status in ACTIVE_PARTICIPANT_STATUSES
            } + additionalRecipients
        }
        val eventId = UUID.randomUUID()
        val occurredAt = Instant.now()
        eventPublisher.publishEvent(
            CallCommittedEvent(
                recipientIds.associateWith { recipientId ->
                    CallEventResponse(
                        eventId = eventId,
                        type = eventTypeByAccountId[recipientId] ?: eventType,
                        call = requireNotNull(responses[recipientId]),
                        occurredAt = occurredAt
                    )
                }
            )
        )
        log.debug(
            "call.event published callId={} eventType={} recipients={} includeAllMembers={}",
            call.id,
            eventType,
            recipientIds.size,
            includeAllMembers
        )
        recipientIds.forEach { recipientId ->
            val recipientEventType = eventTypeByAccountId[recipientId] ?: eventType
            if (recipientEventType != CallEventType.CALL_CREATED &&
                recipientEventType != CallEventType.PARTICIPANT_MEDIA_CHANGED
            ) {
                publishCallPush(
                    requireNotNull(responses[recipientId]),
                    recipientId,
                    recipientEventType,
                    eventId,
                    occurredAt
                )
            }
        }
    }

    private fun publishCallPush(
        response: CallResponse,
        recipientAccountId: UUID,
        eventType: CallEventType,
        eventId: UUID,
        occurredAt: Instant
    ) {
        val viewerParticipant = response.participants.first { it.account.accountId == recipientAccountId }
        val incoming = eventType == CallEventType.INCOMING_CALL
        val ttlSeconds = if (incoming) {
            Duration.between(occurredAt, viewerParticipant.invitedAt.plus(properties.ringTimeout))
                .seconds.coerceAtLeast(0)
        } else {
            CALL_UPDATE_TTL_SECONDS
        }
        pushMessagePublisher.publish(
            PushMessageCommand(
                recipientAccountId,
                null,
                "call:${response.id}:$eventId:$recipientAccountId",
                "CALL",
                if (incoming) "Incoming ${response.type.name.lowercase()} call" else null,
                if (incoming) "${viewerParticipant.invitedBy.displayName} is calling" else null,
                mapOf(
                    "eventType" to "CALL_EVENT",
                    "callEventType" to eventType.name,
                    "callId" to response.id.toString(),
                    "callType" to response.type.name,
                    "eventId" to eventId.toString()
                ),
                true,
                ttlSeconds,
                "call-${response.id}"
            )
        )
    }

    private fun notifyMissedCall(call: ChatCall, participant: ChatCallParticipant) {
        notificationPublisher.publish(
            NotificationCommand(
                participant.accountId,
                participant.invitedByAccountId,
                NotificationType.MISSED_CALL,
                NotificationTargetType.CALL,
                call.id,
                participant.id,
                null,
                "Missed ${call.type.name.lowercase()} call"
            )
        )
    }

    private fun validateSignalPayload(request: CallSignalRequest) {
        when (request.type) {
            CallSignalType.OFFER,
            CallSignalType.ANSWER,
            CallSignalType.ICE_RESTART -> {
                if (request.sdp.isNullOrBlank() || request.candidate != null) {
                    throw IllegalArgumentException("SDP signals require sdp and must not contain a candidate")
                }
            }
            CallSignalType.ICE_CANDIDATE -> {
                if (request.candidate.isNullOrBlank() || request.sdp != null) {
                    throw IllegalArgumentException("ICE candidate signals require candidate and must not contain sdp")
                }
                if (request.sdpMLineIndex != null && request.sdpMLineIndex < 0) {
                    throw IllegalArgumentException("sdpMLineIndex cannot be negative")
                }
            }
        }
    }

    private fun validateConfiguredLimit() {
        check(properties.maxParticipants in 2..HARD_MAX_PARTICIPANTS) {
            "groovsta.calls.max-participants must be between 2 and $HARD_MAX_PARTICIPANTS"
        }
        check(!properties.ringTimeout.isZero && !properties.ringTimeout.isNegative) {
            "groovsta.calls.ring-timeout must be positive"
        }
        check(!properties.heartbeatTimeout.isZero && !properties.heartbeatTimeout.isNegative) {
            "groovsta.calls.heartbeat-timeout must be positive"
        }
    }

    private fun participantCall(callId: UUID, accountId: UUID): ChatCall {
        val call = callRepository.findById(callId).orElseThrow { callNotFound() }
        participantRepository.findByCallIdAndAccountId(call.id, accountId) ?: callNotFound()
        return call
    }

    private fun lockedParticipantCall(callId: UUID, accountId: UUID): ChatCall {
        val call = callRepository.findLockedById(callId) ?: callNotFound()
        participantRepository.findByCallIdAndAccountId(call.id, accountId) ?: callNotFound()
        return call
    }

    private fun requiredParticipant(callId: UUID, accountId: UUID): ChatCallParticipant =
        participantRepository.findByCallIdAndAccountId(callId, accountId) ?: callNotFound()

    private fun requireActiveAccount(accountId: UUID) {
        val account = accountRepository.findById(accountId).orElseThrow { profileNotFound() }
        if (account.status != AccountStatus.ACTIVE) {
            throw IdentityConflictException("Account must be active to use calling")
        }
    }

    private fun callNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Call was not found")

    private fun profileNotFound(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Profile was not found")

    private data class CallCursor(val createdAt: Instant, val id: UUID) {
        fun encode(): String = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$createdAt|$id".toByteArray(Charsets.UTF_8))

        companion object {
            fun from(call: ChatCall) = CallCursor(call.createdAt, call.id)

            fun decode(value: String): CallCursor {
                val decoded = runCatching {
                    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
                }.getOrElse { throw IllegalArgumentException("Invalid call cursor") }
                val parts = decoded.split('|', limit = 2)
                require(parts.size == 2) { "Invalid call cursor" }
                return runCatching { CallCursor(Instant.parse(parts[0]), UUID.fromString(parts[1])) }
                    .getOrElse { throw IllegalArgumentException("Invalid call cursor") }
            }
        }
    }

    private companion object {
        const val HARD_MAX_PARTICIPANTS = 8
        const val CALL_UPDATE_TTL_SECONDS = 60L
        val ACTIVE_PARTICIPANT_STATUSES = setOf(
            CallParticipantStatus.INVITED,
            CallParticipantStatus.JOINED
        )
        val ONGOING_CALL_STATUSES = setOf(CallStatus.RINGING, CallStatus.ACTIVE)
    }
}
