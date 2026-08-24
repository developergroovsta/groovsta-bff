CREATE INDEX idx_identity_follows_followers_page
    ON identity_follows(followed_account_id, created_at DESC, id DESC);

CREATE INDEX idx_identity_follows_following_page
    ON identity_follows(follower_account_id, created_at DESC, id DESC);
