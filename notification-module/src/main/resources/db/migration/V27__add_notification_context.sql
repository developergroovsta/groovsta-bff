ALTER TABLE notifications
    ADD COLUMN context_id UUID;

COMMENT ON COLUMN notifications.context_id IS
    'Optional navigation context such as the comment ID for a comment like or mention.';
