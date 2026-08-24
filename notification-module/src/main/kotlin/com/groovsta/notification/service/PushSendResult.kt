package com.groovsta.notification.service

sealed interface PushSendResult {
    data class Sent(val providerMessageId: String) : PushSendResult
    data class Retryable(val reason: String) : PushSendResult
    data class PermanentFailure(val reason: String, val invalidateRegistration: Boolean) : PushSendResult
    data class Skipped(val reason: String) : PushSendResult
}
