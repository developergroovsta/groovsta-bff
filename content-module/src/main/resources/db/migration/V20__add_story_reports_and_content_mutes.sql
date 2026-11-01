CREATE TABLE identity_content_mutes (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    muted_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    content_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_identity_content_mutes_target
        UNIQUE (account_id, muted_account_id, content_type),
    CONSTRAINT chk_identity_content_mutes_type
        CHECK (content_type IN ('POST', 'REEL', 'STORY')),
    CONSTRAINT chk_identity_content_mutes_not_self
        CHECK (account_id <> muted_account_id)
);

CREATE INDEX idx_identity_content_mutes_lookup
    ON identity_content_mutes(account_id, content_type, muted_account_id);

ALTER TABLE trust_content_reports
    ALTER COLUMN content_id DROP NOT NULL;

ALTER TABLE trust_content_reports
    ADD COLUMN story_id UUID REFERENCES content_stories(id) ON DELETE CASCADE;

ALTER TABLE trust_content_reports
    ADD CONSTRAINT uq_trust_story_reporter UNIQUE (story_id, reporter_account_id),
    ADD CONSTRAINT chk_trust_content_reports_target CHECK (
        (content_id IS NOT NULL AND story_id IS NULL)
        OR (content_id IS NULL AND story_id IS NOT NULL)
    );

CREATE INDEX idx_trust_content_reports_story
    ON trust_content_reports(story_id, status, created_at);

ALTER TABLE content_stories
    ADD COLUMN moderation_reason VARCHAR(1000);

ALTER TABLE content_stories
    DROP CONSTRAINT chk_content_stories_status;

ALTER TABLE content_stories
    ADD CONSTRAINT chk_content_stories_status CHECK (
        status IN ('UPLOADING', 'TRANSCODING', 'PUBLISHED', 'FAILED', 'TAKEN_DOWN', 'DELETED')
    );
