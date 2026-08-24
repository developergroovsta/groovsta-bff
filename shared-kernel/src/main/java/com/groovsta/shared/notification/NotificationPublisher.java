package com.groovsta.shared.notification;

import java.util.UUID;

public interface NotificationPublisher {
    void publish(NotificationCommand command);

    void resolve(NotificationType type, UUID referenceId);
}
