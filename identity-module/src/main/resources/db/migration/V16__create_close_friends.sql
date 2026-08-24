CREATE TABLE identity_close_friends (
    id UUID PRIMARY KEY,
    owner_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    friend_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_identity_close_friends_not_self CHECK (owner_account_id <> friend_account_id),
    CONSTRAINT uq_identity_close_friends_pair UNIQUE (owner_account_id, friend_account_id)
);

CREATE INDEX idx_identity_close_friends_owner_created
    ON identity_close_friends(owner_account_id, created_at DESC, id DESC);

CREATE INDEX idx_identity_close_friends_friend
    ON identity_close_friends(friend_account_id, owner_account_id);
