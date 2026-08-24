CREATE TABLE content_posts (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    caption VARCHAR(2200),
    status VARCHAR(30) NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_content_posts_feed
    ON content_posts(status, published_at DESC, id DESC);
CREATE INDEX idx_content_posts_owner
    ON content_posts(owner_account_id, status, published_at DESC);

CREATE TABLE content_post_media (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    object_key VARCHAR(1024) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_content_post_media_position UNIQUE(post_id, position),
    CONSTRAINT chk_content_post_media_position CHECK(position >= 0),
    CONSTRAINT chk_content_post_media_size CHECK(size_bytes > 0)
);

CREATE INDEX idx_content_post_media_post
    ON content_post_media(post_id, position);
