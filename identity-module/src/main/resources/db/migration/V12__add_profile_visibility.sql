ALTER TABLE identity_profiles
    ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_identity_profiles_public_account
    ON identity_profiles(is_public, account_id);
