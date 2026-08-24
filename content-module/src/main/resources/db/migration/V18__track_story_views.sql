CREATE TABLE content_story_views (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL REFERENCES content_stories(id) ON DELETE CASCADE,
    viewer_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    viewed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_content_story_views_pair UNIQUE (story_id, viewer_account_id)
);

CREATE INDEX idx_content_story_views_viewer_story
    ON content_story_views(viewer_account_id, story_id);

CREATE INDEX idx_content_story_views_story
    ON content_story_views(story_id);
