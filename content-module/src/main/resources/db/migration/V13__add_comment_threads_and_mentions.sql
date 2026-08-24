ALTER TABLE content_comments
    ADD COLUMN thread_root_id UUID,
    ADD COLUMN reply_to_comment_id UUID,
    ADD COLUMN depth INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

UPDATE content_comments
SET thread_root_id = id;

ALTER TABLE content_comments
    ALTER COLUMN thread_root_id SET NOT NULL,
    ADD CONSTRAINT fk_content_comments_thread_root
        FOREIGN KEY (thread_root_id) REFERENCES content_comments(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_content_comments_reply_to
        FOREIGN KEY (reply_to_comment_id) REFERENCES content_comments(id) ON DELETE CASCADE,
    ADD CONSTRAINT chk_content_comments_depth CHECK (depth IN (0, 1)),
    ADD CONSTRAINT chk_content_comments_thread_shape CHECK (
        (depth = 0 AND thread_root_id = id AND reply_to_comment_id IS NULL)
        OR
        (depth = 1 AND thread_root_id <> id AND reply_to_comment_id IS NOT NULL)
    );

CREATE INDEX idx_content_comments_roots
    ON content_comments(content_id, depth, created_at DESC, id DESC);
CREATE INDEX idx_content_comments_thread_replies
    ON content_comments(thread_root_id, depth, created_at, id);

CREATE TABLE content_comment_mentions (
    id UUID PRIMARY KEY,
    comment_id UUID NOT NULL REFERENCES content_comments(id) ON DELETE CASCADE,
    mentioned_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    handle VARCHAR(30) NOT NULL,
    start_index INTEGER NOT NULL,
    end_exclusive INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_content_comment_mention_range CHECK (
        start_index >= 0 AND end_exclusive > start_index
    ),
    CONSTRAINT uq_content_comment_mention_range UNIQUE (comment_id, start_index, end_exclusive)
);

CREATE INDEX idx_content_comment_mentions_comment
    ON content_comment_mentions(comment_id, start_index);
CREATE INDEX idx_content_comment_mentions_account
    ON content_comment_mentions(mentioned_account_id, created_at DESC);
