package com.groovsta.messaging.service

import com.groovsta.messaging.dto.AddCallParticipantsRequest
import com.groovsta.messaging.dto.CallHeartbeatRequest
import com.groovsta.messaging.dto.CallMediaStateRequest
import com.groovsta.messaging.dto.CallPageResponse
import com.groovsta.messaging.dto.CallResponse
import com.groovsta.messaging.dto.CallSignalRequest
import com.groovsta.messaging.dto.CreateCallRequest
import com.groovsta.messaging.dto.IceConfigurationResponse
import java.util.UUID

interface CallService {
    fun create(accountId: UUID, request: CreateCallRequest): CallResponse
    fun get(accountId: UUID, callId: UUID): CallResponse
    fun history(accountId: UUID, cursor: String?, limit: Int): CallPageResponse
    fun active(accountId: UUID): List<CallResponse>
    fun accept(accountId: UUID, callId: UUID): CallResponse
    fun decline(accountId: UUID, callId: UUID): CallResponse
    fun cancel(accountId: UUID, callId: UUID): CallResponse
    fun leave(accountId: UUID, callId: UUID): CallResponse
    fun end(accountId: UUID, callId: UUID): CallResponse
    fun invite(accountId: UUID, callId: UUID, request: AddCallParticipantsRequest): CallResponse
    fun updateMedia(accountId: UUID, callId: UUID, request: CallMediaStateRequest): CallResponse
    fun heartbeat(accountId: UUID, request: CallHeartbeatRequest)
    fun relaySignal(accountId: UUID, request: CallSignalRequest)
    fun iceConfiguration(accountId: UUID): IceConfigurationResponse
    fun expireStaleCalls()
}
