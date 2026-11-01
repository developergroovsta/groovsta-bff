package com.groovsta.shared.notification;

import java.util.UUID;

public interface PushTokenRevoker {
    void disableDevice(UUID identityDeviceId);

    void disableAccount(UUID accountId);
}
