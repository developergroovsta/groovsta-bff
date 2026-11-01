package com.groovsta.notification.service

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PushDeliveryProcessor(
    private val loader: PushDeliveryLoader,
    private val gateway: PushGateway,
    private val outcomeService: PushDeliveryOutcomeService
) {
    fun process(deliveryId: UUID) {
        val work = loader.load(deliveryId)
        if (work == null) {
            outcomeService.complete(deliveryId, PushSendResult.Skipped("REGISTRATION_DISABLED_OR_MISSING"))
            return
        }
        outcomeService.complete(deliveryId, gateway.send(work))
    }
}
