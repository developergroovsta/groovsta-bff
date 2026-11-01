ALTER TABLE chat_messages
    ADD COLUMN reply_to_message_id UUID,
    ADD COLUMN edited_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN unsent_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN delivered_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_message_reply
        FOREIGN KEY (reply_to_message_id) REFERENCES chat_messages(id) ON DELETE SET NULL;

CREATE INDEX idx_chat_messages_reply
    ON chat_messages(reply_to_message_id)
    WHERE reply_to_message_id IS NOT NULL;

CREATE INDEX idx_chat_messages_pending_delivery
    ON chat_messages(conversation_id, sender_account_id, created_at)
    WHERE delivered_at IS NULL;

CREATE TABLE chat_message_reactions (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    emoji VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_chat_message_reaction_account UNIQUE (message_id, account_id),
    CONSTRAINT chk_chat_message_reaction_emoji CHECK (char_length(trim(emoji)) > 0)
);

CREATE INDEX idx_chat_message_reactions_message
    ON chat_message_reactions(message_id);
