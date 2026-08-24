ALTER TABLE content_reel_likes RENAME TO content_likes;
ALTER TABLE content_likes RENAME COLUMN reel_id TO content_id;
ALTER TABLE content_likes RENAME CONSTRAINT uq_content_reel_like TO uq_content_like;
ALTER TABLE content_likes RENAME CONSTRAINT fk_content_reel_likes_item TO fk_content_likes_item;
ALTER INDEX idx_content_reel_likes_reel RENAME TO idx_content_likes_content;

ALTER TABLE content_reel_comments RENAME TO content_comments;
ALTER TABLE content_comments RENAME COLUMN reel_id TO content_id;
ALTER TABLE content_comments RENAME CONSTRAINT fk_content_reel_comments_item TO fk_content_comments_item;
ALTER INDEX idx_content_reel_comments_reel RENAME TO idx_content_comments_content;

ALTER TABLE trust_reel_reports RENAME TO trust_content_reports;
ALTER TABLE trust_content_reports RENAME COLUMN reel_id TO content_id;
ALTER TABLE trust_content_reports RENAME CONSTRAINT uq_trust_reel_reporter TO uq_trust_content_reporter;
ALTER TABLE trust_content_reports RENAME CONSTRAINT fk_trust_reel_reports_item TO fk_trust_content_reports_item;
ALTER INDEX idx_trust_reel_reports_pending RENAME TO idx_trust_content_reports_pending;
ALTER INDEX idx_trust_reel_reports_reel RENAME TO idx_trust_content_reports_content;
