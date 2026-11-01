ALTER TABLE content_stories
    ADD COLUMN linked_story_id UUID;

CREATE INDEX idx_content_stories_linked_story
    ON content_stories(linked_story_id);

ALTER TABLE content_stories
    ADD CONSTRAINT chk_content_stories_share_source
    CHECK (
        (owns_media = TRUE AND linked_story_id IS NULL)
        OR (
            owns_media = FALSE
            AND (
                (linked_content_id IS NOT NULL AND linked_story_id IS NULL)
                OR (linked_content_id IS NULL AND linked_story_id IS NOT NULL)
            )
        )
    );

COMMENT ON COLUMN content_stories.linked_story_id IS
    'Stable source story identifier without a foreign key so a deleted source remains identifiable as unavailable.';
