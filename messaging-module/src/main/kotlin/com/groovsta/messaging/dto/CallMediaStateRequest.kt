package com.groovsta.messaging.dto

data class CallMediaStateRequest(
    val microphoneMuted: Boolean,
    val cameraEnabled: Boolean
)
