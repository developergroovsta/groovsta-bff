package com.groovsta.shared.notification;

import java.util.Map;
import java.util.UUID;

public record PushMessageCommand(
        UUID recipientAccountId,
        UUID notificationId,
        String deduplicationKey,
        String messageType,
        String title,
        String body,
        Map<String, String> data,
        boolean highPriority,
        long ttlSeconds,
        String collapseKey
) {
    public PushMessageCommand {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
