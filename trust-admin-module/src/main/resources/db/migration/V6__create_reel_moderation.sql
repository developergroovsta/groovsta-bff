CREATE TABLE trust_moderation_reviews (
    id UUID PRIMARY KEY,
    reel_id UUID NOT NULL UNIQUE,
    owner_account_id UUID NOT NULL,
    media_object_key VARCHAR(1024) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(1000),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_trust_moderation_pending ON trust_moderation_reviews(status, created_at);
