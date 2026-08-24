ALTER TABLE chat_messages
    DROP CONSTRAINT chk_chat_message_payload;

ALTER TABLE chat_messages
    ADD CONSTRAINT chk_chat_message_payload CHECK (
        (message_type = 'TEXT' AND message_text IS NOT NULL AND shared_content_id IS NULL)
        OR
        (message_type = 'CONTENT_SHARE' AND shared_content_id IS NOT NULL)
        OR
        (message_type = 'MEDIA' AND shared_content_id IS NULL)
    );

CREATE TABLE chat_attachment_upload_batches (
    id UUID PRIMARY KEY,
    sender_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    recipient_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    client_message_id UUID NOT NULL,
    message_id UUID UNIQUE REFERENCES chat_messages(id) ON DELETE SET NULL,
    message_text VARCHAR(2000),
    reply_to_message_id UUID REFERENCES chat_messages(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_chat_attachment_batch_client_message
        UNIQUE (sender_account_id, client_message_id),
    CONSTRAINT chk_chat_attachment_batch_participants
        CHECK (sender_account_id <> recipient_account_id),
    CONSTRAINT chk_chat_attachment_batch_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT chk_chat_attachment_batch_completion CHECK (
        (status = 'COMPLETED' AND message_id IS NOT NULL AND completed_at IS NOT NULL)
        OR
        (status <> 'COMPLETED' AND message_id IS NULL AND completed_at IS NULL)
    )
);

CREATE TABLE chat_message_attachments (
    id UUID PRIMARY KEY,
    upload_batch_id UUID NOT NULL REFERENCES chat_attachment_upload_batches(id) ON DELETE CASCADE,
    message_id UUID REFERENCES chat_messages(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    media_type VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    source_object_key VARCHAR(1024) NOT NULL,
    media_object_key VARCHAR(1024),
    thumbnail_object_key VARCHAR(1024),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    expected_size_bytes BIGINT NOT NULL,
    actual_size_bytes BIGINT,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    duration_millis BIGINT,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_chat_attachment_batch_position UNIQUE (upload_batch_id, position),
    CONSTRAINT uq_chat_attachment_source_key UNIQUE (source_object_key),
    CONSTRAINT uq_chat_attachment_message_position UNIQUE (message_id, position),
    CONSTRAINT chk_chat_attachment_media_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT chk_chat_attachment_status
        CHECK (status IN ('UPLOADING', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT chk_chat_attachment_dimensions CHECK (width > 0 AND height > 0),
    CONSTRAINT chk_chat_attachment_sizes CHECK (
        expected_size_bytes > 0 AND (actual_size_bytes IS NULL OR actual_size_bytes > 0)
    ),
    CONSTRAINT chk_chat_attachment_duration CHECK (
        (media_type = 'IMAGE' AND duration_millis IS NULL)
        OR
        (media_type = 'VIDEO' AND (duration_millis IS NULL OR duration_millis > 0))
    ),
    CONSTRAINT chk_chat_attachment_linkage CHECK (
        (status = 'UPLOADING' AND message_id IS NULL)
        OR
        (status <> 'UPLOADING' AND message_id IS NOT NULL)
    )
);

CREATE INDEX idx_chat_attachments_message
    ON chat_message_attachments(message_id, position)
    WHERE message_id IS NOT NULL;

CREATE INDEX idx_chat_attachment_batches_expiry
    ON chat_attachment_upload_batches(status, expires_at)
    WHERE status = 'PENDING';

ALTER TABLE content_transcode_jobs
    DROP CONSTRAINT chk_content_transcode_jobs_target;

ALTER TABLE content_transcode_jobs
    ADD COLUMN chat_attachment_id UUID UNIQUE
        REFERENCES chat_message_attachments(id) ON DELETE CASCADE;

ALTER TABLE content_transcode_jobs
    ADD CONSTRAINT chk_content_transcode_jobs_target CHECK (
        num_nonnulls(reel_id, story_id, chat_attachment_id) = 1
    );
