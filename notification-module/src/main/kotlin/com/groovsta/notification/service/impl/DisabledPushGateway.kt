package com.groovsta.notification.service.impl

import com.groovsta.notification.service.PushDeliveryWork
import com.groovsta.notification.service.PushGateway
import com.groovsta.notification.service.PushSendResult
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "groovsta.push",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true
)
class DisabledPushGateway : PushGateway {
    override fun send(work: PushDeliveryWork): PushSendResult =
        PushSendResult.Skipped("FCM_DISABLED")
}
