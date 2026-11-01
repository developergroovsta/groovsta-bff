ALTER TABLE content_items
    ADD COLUMN comments_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE content_stories
    ADD COLUMN comments_enabled BOOLEAN NOT NULL DEFAULT TRUE;
