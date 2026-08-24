CREATE TABLE content_stories (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL,
    visibility VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    caption VARCHAR(2200),
    source_object_key VARCHAR(1024) NOT NULL,
    published_object_key VARCHAR(1024),
    thumbnail_object_key VARCHAR(1024),
    content_type VARCHAR(100) NOT NULL,
    source_size_bytes BIGINT NOT NULL,
    duration_seconds INTEGER,
    linked_content_id UUID REFERENCES content_items(id) ON DELETE SET NULL,
    owns_media BOOLEAN NOT NULL,
    failure_reason VARCHAR(1000),
    published_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_content_stories_media_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT chk_content_stories_visibility CHECK (visibility IN ('EVERYONE', 'FOLLOWERS', 'CLOSE_FRIENDS')),
    CONSTRAINT chk_content_stories_status CHECK (status IN ('UPLOADING', 'TRANSCODING', 'PUBLISHED', 'FAILED', 'DELETED')),
    CONSTRAINT chk_content_stories_publication CHECK (
        (published_at IS NULL AND expires_at IS NULL)
        OR (published_at IS NOT NULL AND expires_at = published_at + INTERVAL '24 hours')
    )
);

CREATE INDEX idx_content_stories_visible
    ON content_stories(status, expires_at, published_at DESC, id DESC);

CREATE INDEX idx_content_stories_owner_visible
    ON content_stories(owner_account_id, status, expires_at, published_at DESC, id DESC);

CREATE TABLE content_story_mentions (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL REFERENCES content_stories(id) ON DELETE CASCADE,
    mentioned_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    handle VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_content_story_mentions_pair UNIQUE (story_id, mentioned_account_id)
);

CREATE INDEX idx_content_story_mentions_story
    ON content_story_mentions(story_id);

CREATE INDEX idx_content_story_mentions_account
    ON content_story_mentions(mentioned_account_id, story_id);

ALTER TABLE content_transcode_jobs
    ALTER COLUMN reel_id DROP NOT NULL;

ALTER TABLE content_transcode_jobs
    ADD COLUMN story_id UUID UNIQUE REFERENCES content_stories(id) ON DELETE CASCADE;

ALTER TABLE content_transcode_jobs
    ADD CONSTRAINT chk_content_transcode_jobs_target
    CHECK (
        (reel_id IS NOT NULL AND story_id IS NULL)
        OR (reel_id IS NULL AND story_id IS NOT NULL)
    );
