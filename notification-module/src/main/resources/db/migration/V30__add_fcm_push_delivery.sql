CREATE TABLE notification_push_registrations (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    identity_device_id UUID NOT NULL REFERENCES identity_devices(id) ON DELETE CASCADE,
    registration_id VARCHAR(4096) NOT NULL,
    identifier_type VARCHAR(40) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    disabled_at TIMESTAMP WITH TIME ZONE,
    failure_count INTEGER NOT NULL DEFAULT 0,
    last_failure_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_push_device UNIQUE (identity_device_id),
    CONSTRAINT chk_notification_push_platform CHECK (platform IN ('ANDROID', 'IOS', 'WEB')),
    CONSTRAINT chk_notification_push_identifier_type
        CHECK (identifier_type IN ('FCM_TOKEN', 'FIREBASE_INSTALLATION_ID')),
    CONSTRAINT chk_notification_push_failure_count CHECK (failure_count >= 0)
);

CREATE INDEX idx_notification_push_active_account
    ON notification_push_registrations(account_id, id)
    WHERE disabled_at IS NULL;

CREATE UNIQUE INDEX uq_notification_push_active_registration_id
    ON notification_push_registrations(registration_id)
    WHERE disabled_at IS NULL;

CREATE TABLE notification_push_deliveries (
    id UUID PRIMARY KEY,
    push_registration_id UUID NOT NULL
        REFERENCES notification_push_registrations(id) ON DELETE CASCADE,
    recipient_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    notification_id UUID REFERENCES notifications(id) ON DELETE CASCADE,
    deduplication_key VARCHAR(255) NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    title VARCHAR(160),
    body VARCHAR(320),
    data_payload TEXT NOT NULL,
    high_priority BOOLEAN NOT NULL,
    ttl_seconds BIGINT NOT NULL,
    collapse_key VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_until TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE,
    provider_message_id VARCHAR(512),
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notification_push_delivery
        UNIQUE (push_registration_id, deduplication_key),
    CONSTRAINT chk_notification_push_delivery_attempts CHECK (attempt_count >= 0),
    CONSTRAINT chk_notification_push_delivery_ttl CHECK (ttl_seconds BETWEEN 0 AND 2419200)
);

CREATE INDEX idx_notification_push_delivery_claim
    ON notification_push_deliveries(status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'RETRY', 'PROCESSING');

CREATE INDEX idx_notification_push_delivery_notification
    ON notification_push_deliveries(notification_id)
    WHERE notification_id IS NOT NULL;
