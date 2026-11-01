ALTER TABLE chat_call_participants
    ADD COLUMN invited_by_account_id UUID;

UPDATE chat_call_participants participant
SET invited_by_account_id = call.initiator_account_id
FROM chat_calls call
WHERE call.id = participant.call_id;

ALTER TABLE chat_call_participants
    ALTER COLUMN invited_by_account_id SET NOT NULL,
    ADD CONSTRAINT fk_chat_call_participant_inviter
        FOREIGN KEY (invited_by_account_id) REFERENCES identity_accounts(id) ON DELETE CASCADE;

CREATE INDEX idx_chat_call_participants_inviter
    ON chat_call_participants(invited_by_account_id);
