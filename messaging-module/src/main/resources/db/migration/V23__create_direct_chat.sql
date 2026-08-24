CREATE TABLE chat_conversations (
    id UUID PRIMARY KEY,
    participant_one_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    participant_two_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    last_message_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_chat_conversation_distinct_participants
        CHECK (participant_one_account_id < participant_two_account_id),
    CONSTRAINT uq_chat_conversation_participants
        UNIQUE (participant_one_account_id, participant_two_account_id)
);

CREATE TABLE chat_conversation_members (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    last_read_message_id UUID,
    last_read_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_chat_conversation_member UNIQUE (conversation_id, account_id)
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    sender_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    client_message_id UUID NOT NULL,
    message_type VARCHAR(30) NOT NULL,
    message_text VARCHAR(2000),
    shared_content_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_chat_sender_client_message UNIQUE (sender_account_id, client_message_id),
    CONSTRAINT chk_chat_message_payload CHECK (
        (message_type = 'TEXT' AND message_text IS NOT NULL AND shared_content_id IS NULL)
        OR
        (message_type = 'CONTENT_SHARE' AND shared_content_id IS NOT NULL)
    )
);

ALTER TABLE chat_conversations
    ADD CONSTRAINT fk_chat_conversation_last_message
        FOREIGN KEY (last_message_id) REFERENCES chat_messages(id) ON DELETE SET NULL;

ALTER TABLE chat_conversation_members
    ADD CONSTRAINT fk_chat_member_last_read_message
        FOREIGN KEY (last_read_message_id) REFERENCES chat_messages(id) ON DELETE SET NULL;

CREATE INDEX idx_chat_conversations_participant_one_page
    ON chat_conversations(participant_one_account_id, updated_at DESC, id DESC)
    WHERE last_message_id IS NOT NULL;

CREATE INDEX idx_chat_conversations_participant_two_page
    ON chat_conversations(participant_two_account_id, updated_at DESC, id DESC)
    WHERE last_message_id IS NOT NULL;

CREATE INDEX idx_chat_messages_conversation_page
    ON chat_messages(conversation_id, created_at DESC, id DESC);

CREATE INDEX idx_chat_messages_shared_content
    ON chat_messages(shared_content_id)
    WHERE shared_content_id IS NOT NULL;

CREATE INDEX idx_chat_members_account
    ON chat_conversation_members(account_id, conversation_id);
