package com.groovsta.shared.notification;

public interface PushMessagePublisher {
    void publish(PushMessageCommand command);
}
