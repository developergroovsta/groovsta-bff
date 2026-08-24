CREATE TABLE content_comment_likes (
    id UUID PRIMARY KEY,
    comment_id UUID NOT NULL REFERENCES content_comments(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_content_comment_like UNIQUE (comment_id, account_id)
);

CREATE INDEX idx_content_comment_likes_comment
    ON content_comment_likes(comment_id);
CREATE INDEX idx_content_comment_likes_account
    ON content_comment_likes(account_id, created_at DESC);
