ALTER TABLE content_likes
    ALTER COLUMN content_id DROP NOT NULL;

ALTER TABLE content_likes
    ADD COLUMN story_id UUID REFERENCES content_stories(id) ON DELETE CASCADE;

ALTER TABLE content_likes
    ADD CONSTRAINT uq_content_like_story UNIQUE (story_id, account_id),
    ADD CONSTRAINT chk_content_likes_target CHECK (
        (content_id IS NOT NULL AND story_id IS NULL)
        OR (content_id IS NULL AND story_id IS NOT NULL)
    );

CREATE INDEX idx_content_likes_story
    ON content_likes(story_id, created_at DESC, id DESC);

CREATE INDEX idx_content_likes_content_page
    ON content_likes(content_id, created_at DESC, id DESC);

ALTER TABLE content_comments
    ALTER COLUMN content_id DROP NOT NULL;

ALTER TABLE content_comments
    ADD COLUMN story_id UUID REFERENCES content_stories(id) ON DELETE CASCADE;

ALTER TABLE content_comments
    ADD CONSTRAINT chk_content_comments_target CHECK (
        (content_id IS NOT NULL AND story_id IS NULL)
        OR (content_id IS NULL AND story_id IS NOT NULL)
    );

CREATE INDEX idx_content_comments_story_roots
    ON content_comments(story_id, depth, created_at DESC, id DESC);

CREATE INDEX idx_content_comments_story_thread_replies
    ON content_comments(story_id, thread_root_id, depth, created_at, id);
