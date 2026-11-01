CREATE TABLE content_shares (
    id UUID PRIMARY KEY,
    content_id UUID REFERENCES content_items(id) ON DELETE CASCADE,
    story_id UUID REFERENCES content_stories(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    channel VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_content_shares_target CHECK (
        (content_id IS NOT NULL AND story_id IS NULL)
        OR (content_id IS NULL AND story_id IS NOT NULL)
    ),
    CONSTRAINT uq_content_shares_content_account UNIQUE (content_id, account_id),
    CONSTRAINT uq_content_shares_story_account UNIQUE (story_id, account_id)
);

CREATE INDEX idx_content_shares_content_page
    ON content_shares(content_id, created_at DESC, id DESC)
    WHERE content_id IS NOT NULL;

CREATE INDEX idx_content_shares_story_page
    ON content_shares(story_id, created_at DESC, id DESC)
    WHERE story_id IS NOT NULL;

CREATE INDEX idx_content_shares_account
    ON content_shares(account_id, created_at DESC);
