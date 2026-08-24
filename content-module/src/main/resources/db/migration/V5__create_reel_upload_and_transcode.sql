CREATE TABLE content_reels (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    caption VARCHAR(2200),
    source_object_key VARCHAR(1024) NOT NULL,
    published_object_key VARCHAR(1024),
    thumbnail_object_key VARCHAR(1024),
    content_type VARCHAR(100) NOT NULL,
    source_size_bytes BIGINT NOT NULL,
    duration_seconds INTEGER,
    status VARCHAR(30) NOT NULL,
    moderation_reason VARCHAR(1000),
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_content_reels_feed ON content_reels(status, published_at DESC, id DESC);
CREATE INDEX idx_content_reels_owner ON content_reels(owner_account_id, status, published_at DESC);

CREATE TABLE content_transcode_jobs (
    id UUID PRIMARY KEY,
    reel_id UUID NOT NULL UNIQUE REFERENCES content_reels(id) ON DELETE CASCADE,
    source_object_key VARCHAR(1024) NOT NULL,
    output_prefix VARCHAR(1024) NOT NULL,
    status VARCHAR(20) NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_content_transcode_queue ON content_transcode_jobs(status, created_at);
