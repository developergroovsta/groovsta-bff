package com.groovsta.notification.service

interface PushGateway {
    fun send(work: PushDeliveryWork): PushSendResult
}
