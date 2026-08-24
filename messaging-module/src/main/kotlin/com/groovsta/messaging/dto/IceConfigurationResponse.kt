package com.groovsta.messaging.dto

data class IceConfigurationResponse(
    val iceServers: List<IceServerResponse>,
    val groupParticipantLimit: Int,
    val heartbeatIntervalSeconds: Long
)
