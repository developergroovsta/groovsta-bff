CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    actor_account_id UUID REFERENCES identity_accounts(id) ON DELETE SET NULL,
    notification_type VARCHAR(40) NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id UUID NOT NULL,
    reference_id UUID NOT NULL,
    preview VARCHAR(280),
    read_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_notifications_type_reference UNIQUE (notification_type, reference_id)
);

CREATE INDEX idx_notifications_recipient_inbox
    ON notifications(recipient_account_id, created_at DESC, id DESC);

CREATE INDEX idx_notifications_recipient_unread
    ON notifications(recipient_account_id, created_at DESC, id DESC)
    WHERE read_at IS NULL;
