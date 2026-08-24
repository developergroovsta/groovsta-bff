CREATE TABLE content_item_tags (
    id UUID PRIMARY KEY,
    content_id UUID NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    tagged_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    handle VARCHAR(30) NOT NULL,
    position INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_content_item_tags_account UNIQUE (content_id, tagged_account_id),
    CONSTRAINT uq_content_item_tags_position UNIQUE (content_id, position),
    CONSTRAINT chk_content_item_tags_position CHECK (position >= 0)
);

CREATE INDEX idx_content_item_tags_content
    ON content_item_tags(content_id, position);

CREATE INDEX idx_content_item_tags_account
    ON content_item_tags(tagged_account_id, created_at DESC);
