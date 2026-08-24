CREATE TABLE content_reel_likes (
    id UUID PRIMARY KEY,
    reel_id UUID NOT NULL REFERENCES content_reels(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_content_reel_like UNIQUE (reel_id, account_id)
);

CREATE INDEX idx_content_reel_likes_reel ON content_reel_likes(reel_id);

CREATE TABLE content_reel_comments (
    id UUID PRIMARY KEY,
    reel_id UUID NOT NULL REFERENCES content_reels(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_content_reel_comments_reel ON content_reel_comments(reel_id, created_at DESC);
