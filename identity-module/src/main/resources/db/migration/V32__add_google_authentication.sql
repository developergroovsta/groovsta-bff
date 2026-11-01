ALTER TABLE identity_accounts
    ALTER COLUMN password_hash DROP NOT NULL;

CREATE TABLE identity_external_identities (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(128) NOT NULL,
    provider_email VARCHAR(320) NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_identity_external_provider CHECK (provider IN ('GOOGLE')),
    CONSTRAINT uq_identity_external_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_identity_external_account_provider UNIQUE (account_id, provider)
);

CREATE INDEX idx_identity_external_identities_account
    ON identity_external_identities(account_id);

CREATE TABLE identity_external_auth_registrations (
    id UUID PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    provider_subject VARCHAR(128) NOT NULL,
    email VARCHAR(320) NOT NULL,
    suggested_display_name VARCHAR(80),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    completed_account_id UUID REFERENCES identity_accounts(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_identity_external_registration_provider CHECK (provider IN ('GOOGLE'))
);

CREATE INDEX idx_identity_external_registration_subject
    ON identity_external_auth_registrations(provider, provider_subject, created_at DESC);

CREATE INDEX idx_identity_external_registration_expiry
    ON identity_external_auth_registrations(expires_at)
    WHERE consumed_at IS NULL;
