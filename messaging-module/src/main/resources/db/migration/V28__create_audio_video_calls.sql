CREATE TABLE chat_calls (
    id UUID PRIMARY KEY,
    initiator_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    client_call_id UUID NOT NULL,
    call_type VARCHAR(10) NOT NULL,
    status VARCHAR(12) NOT NULL,
    max_participants INTEGER NOT NULL,
    ring_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    ended_by_account_id UUID REFERENCES identity_accounts(id) ON DELETE SET NULL,
    end_reason VARCHAR(12),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_chat_call_initiator_client_id UNIQUE (initiator_account_id, client_call_id),
    CONSTRAINT chk_chat_call_max_participants CHECK (max_participants BETWEEN 2 AND 8),
    CONSTRAINT chk_chat_call_lifecycle CHECK (
        (status <> 'ACTIVE' OR started_at IS NOT NULL)
        AND
        (status <> 'ENDED' OR (ended_at IS NOT NULL AND end_reason IS NOT NULL))
    )
);

CREATE TABLE chat_call_participants (
    id UUID PRIMARY KEY,
    call_id UUID NOT NULL REFERENCES chat_calls(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    initial_participant BOOLEAN NOT NULL,
    role VARCHAR(10) NOT NULL,
    status VARCHAR(12) NOT NULL,
    invited_at TIMESTAMP WITH TIME ZONE NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE,
    left_at TIMESTAMP WITH TIME ZONE,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    microphone_muted BOOLEAN NOT NULL DEFAULT FALSE,
    camera_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_chat_call_participant UNIQUE (call_id, account_id),
    CONSTRAINT chk_chat_call_participant_joined CHECK (
        status <> 'JOINED' OR joined_at IS NOT NULL
    )
);

CREATE INDEX idx_chat_call_participant_account_history
    ON chat_call_participants(account_id, call_id);

CREATE INDEX idx_chat_call_participant_active_account
    ON chat_call_participants(account_id, status)
    WHERE status IN ('INVITED', 'JOINED');

CREATE INDEX idx_chat_calls_history
    ON chat_calls(created_at DESC, id DESC);

CREATE INDEX idx_chat_calls_ring_expiry
    ON chat_calls(ring_expires_at)
    WHERE status = 'RINGING';

CREATE INDEX idx_chat_call_participant_heartbeat
    ON chat_call_participants(last_heartbeat_at)
    WHERE status = 'JOINED';
