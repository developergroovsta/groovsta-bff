ALTER TABLE identity_follows
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'FOLLOWING';

ALTER TABLE identity_follows
    ADD CONSTRAINT chk_identity_follows_status
        CHECK (status IN ('REQUESTED', 'FOLLOWING'));

CREATE INDEX idx_identity_follows_pending_requests
    ON identity_follows(followed_account_id, created_at DESC, id DESC)
    WHERE status = 'REQUESTED';
