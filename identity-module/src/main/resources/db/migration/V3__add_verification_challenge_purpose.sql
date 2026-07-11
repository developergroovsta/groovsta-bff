ALTER TABLE identity_verification_challenges
    ADD COLUMN purpose VARCHAR(30) NOT NULL DEFAULT 'ACCOUNT_VERIFICATION';

CREATE INDEX idx_identity_verification_open_by_purpose
    ON identity_verification_challenges(account_id, purpose, channel, destination, created_at DESC);
