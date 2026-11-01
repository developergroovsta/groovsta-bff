package com.groovsta.notification.model

enum class PushDeliveryStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SENT,
    SKIPPED,
    DEAD
}
