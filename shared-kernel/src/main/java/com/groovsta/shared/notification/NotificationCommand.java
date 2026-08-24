package com.groovsta.shared.notification;

import java.util.UUID;

public record NotificationCommand(
        UUID recipientAccountId,
        UUID actorAccountId,
        NotificationType type,
        NotificationTargetType targetType,
        UUID targetId,
        UUID referenceId,
        UUID contextId,
        String preview
) {
    public NotificationCommand(
            UUID recipientAccountId,
            UUID actorAccountId,
            NotificationType type,
            NotificationTargetType targetType,
            UUID targetId,
            UUID referenceId,
            String preview
    ) {
        this(recipientAccountId, actorAccountId, type, targetType, targetId, referenceId, null, preview);
    }
}
