CREATE INDEX idx_content_stories_owner_archive
    ON content_stories(owner_account_id, status, published_at DESC, id DESC);

CREATE TABLE content_story_highlights (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_content_story_highlights_name CHECK (length(trim(name)) > 0)
);

CREATE INDEX idx_content_story_highlights_owner
    ON content_story_highlights(owner_account_id, created_at DESC, id DESC);

CREATE TABLE content_story_highlight_items (
    id UUID PRIMARY KEY,
    highlight_id UUID NOT NULL REFERENCES content_story_highlights(id) ON DELETE CASCADE,
    story_id UUID NOT NULL REFERENCES content_stories(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_content_story_highlight_item UNIQUE (highlight_id, story_id),
    CONSTRAINT uq_content_story_highlight_position UNIQUE (highlight_id, position),
    CONSTRAINT chk_content_story_highlight_position CHECK (position >= 0)
);

CREATE INDEX idx_content_story_highlight_items_highlight
    ON content_story_highlight_items(highlight_id, position);

CREATE INDEX idx_content_story_highlight_items_story
    ON content_story_highlight_items(story_id);
