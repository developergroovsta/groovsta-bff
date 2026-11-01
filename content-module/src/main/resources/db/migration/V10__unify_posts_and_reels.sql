CREATE TABLE content_items (
    id UUID PRIMARY KEY,
    content_kind VARCHAR(20) NOT NULL,
    owner_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    caption VARCHAR(2200),
    status VARCHAR(30) NOT NULL,
    moderation_reason VARCHAR(1000),
    published_at TIMESTAMP WITH TIME ZONE,
    source_object_key VARCHAR(1024),
    published_object_key VARCHAR(1024),
    thumbnail_object_key VARCHAR(1024),
    content_type VARCHAR(100),
    source_size_bytes BIGINT,
    duration_seconds INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_content_items_kind CHECK (content_kind IN ('POST', 'REEL')),
    CONSTRAINT chk_content_items_reel_source CHECK (
        content_kind <> 'REEL'
        OR (source_object_key IS NOT NULL AND content_type IS NOT NULL AND source_size_bytes IS NOT NULL)
    )
);

INSERT INTO content_items (
    id,
    content_kind,
    owner_account_id,
    caption,
    status,
    moderation_reason,
    published_at,
    source_object_key,
    published_object_key,
    thumbnail_object_key,
    content_type,
    source_size_bytes,
    duration_seconds,
    created_at,
    updated_at
)
SELECT
    id,
    'REEL',
    owner_account_id,
    caption,
    status,
    moderation_reason,
    published_at,
    source_object_key,
    published_object_key,
    thumbnail_object_key,
    content_type,
    source_size_bytes,
    duration_seconds,
    created_at,
    updated_at
FROM content_reels;

INSERT INTO content_items (
    id,
    content_kind,
    owner_account_id,
    caption,
    status,
    published_at,
    created_at,
    updated_at
)
SELECT
    id,
    'POST',
    owner_account_id,
    caption,
    status,
    published_at,
    created_at,
    updated_at
FROM content_posts;

CREATE INDEX idx_content_items_home_feed
    ON content_items(status, published_at DESC, id DESC);
CREATE INDEX idx_content_items_owner_feed
    ON content_items(owner_account_id, status, published_at DESC, id DESC);
CREATE INDEX idx_content_items_kind_feed
    ON content_items(content_kind, status, published_at DESC, id DESC);

ALTER TABLE content_transcode_jobs
    DROP CONSTRAINT IF EXISTS content_transcode_jobs_reel_id_fkey;
ALTER TABLE content_transcode_jobs
    ADD CONSTRAINT fk_content_transcode_jobs_item
    FOREIGN KEY (reel_id) REFERENCES content_items(id) ON DELETE CASCADE;

ALTER TABLE content_reel_likes
    DROP CONSTRAINT IF EXISTS content_reel_likes_reel_id_fkey;
ALTER TABLE content_reel_likes
    ADD CONSTRAINT fk_content_reel_likes_item
    FOREIGN KEY (reel_id) REFERENCES content_items(id) ON DELETE CASCADE;

ALTER TABLE content_reel_comments
    DROP CONSTRAINT IF EXISTS content_reel_comments_reel_id_fkey;
ALTER TABLE content_reel_comments
    ADD CONSTRAINT fk_content_reel_comments_item
    FOREIGN KEY (reel_id) REFERENCES content_items(id) ON DELETE CASCADE;

ALTER TABLE trust_reel_reports
    DROP CONSTRAINT IF EXISTS trust_reel_reports_reel_id_fkey;
ALTER TABLE trust_reel_reports
    ADD CONSTRAINT fk_trust_reel_reports_item
    FOREIGN KEY (reel_id) REFERENCES content_items(id) ON DELETE CASCADE;

ALTER TABLE content_post_media
    DROP CONSTRAINT IF EXISTS content_post_media_post_id_fkey;
ALTER TABLE content_post_media
    ADD CONSTRAINT fk_content_post_media_item
    FOREIGN KEY (post_id) REFERENCES content_items(id) ON DELETE CASCADE;

DROP TABLE content_reels;
DROP TABLE content_posts;
