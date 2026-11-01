UPDATE content_reels
SET status = 'READY_TO_PUBLISH'
WHERE status = 'PENDING_MODERATION';

UPDATE content_reels
SET status = 'FAILED'
WHERE status = 'REJECTED';

DROP TABLE trust_moderation_reviews;

CREATE TABLE trust_reel_reports (
    id UUID PRIMARY KEY,
    reel_id UUID NOT NULL REFERENCES content_reels(id) ON DELETE CASCADE,
    reporter_account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    category VARCHAR(40) NOT NULL,
    details VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    resolution VARCHAR(20) NOT NULL,
    moderator_reason VARCHAR(1000),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_trust_reel_reporter UNIQUE (reel_id, reporter_account_id)
);

CREATE INDEX idx_trust_reel_reports_pending ON trust_reel_reports(status, created_at);
CREATE INDEX idx_trust_reel_reports_reel ON trust_reel_reports(reel_id, status);
