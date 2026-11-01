CREATE TABLE content_saved_items (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_content_saved_items_account_content UNIQUE (account_id, content_id)
);

CREATE INDEX idx_content_saved_items_account_page
    ON content_saved_items(account_id, created_at DESC, id DESC);

CREATE INDEX idx_content_saved_items_content
    ON content_saved_items(content_id);
