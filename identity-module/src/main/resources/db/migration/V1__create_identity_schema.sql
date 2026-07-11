CREATE TABLE identity_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    email_verified_at TIMESTAMP WITH TIME ZONE,
    phone_number VARCHAR(32),
    phone_verified_at TIMESTAMP WITH TIME ZONE,
    adult_declared_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE identity_profiles (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL UNIQUE REFERENCES identity_accounts(id) ON DELETE CASCADE,
    handle VARCHAR(30) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    bio VARCHAR(500),
    avatar_url VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE identity_devices (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    device_identifier VARCHAR(255) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    app_version VARCHAR(64),
    last_user_agent VARCHAR(1024),
    first_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_identity_device_account_identifier UNIQUE (account_id, device_identifier)
);

CREATE TABLE identity_sessions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    device_id UUID REFERENCES identity_devices(id) ON DELETE SET NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_identity_sessions_account_id ON identity_sessions(account_id);

CREATE TABLE identity_verification_challenges (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    destination VARCHAR(320) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count INTEGER NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_identity_verification_open
    ON identity_verification_challenges(account_id, channel, destination, created_at DESC);

CREATE TABLE identity_follows (
    id UUID PRIMARY KEY,
    follower_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    followed_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_identity_follows_not_self CHECK (follower_account_id <> followed_account_id),
    CONSTRAINT uq_identity_follows_pair UNIQUE (follower_account_id, followed_account_id)
);

CREATE INDEX idx_identity_follows_followed ON identity_follows(followed_account_id);
