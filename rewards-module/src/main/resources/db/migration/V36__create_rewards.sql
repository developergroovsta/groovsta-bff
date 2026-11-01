ALTER TABLE identity_follows
    ADD COLUMN IF NOT EXISTS followed_at TIMESTAMP WITH TIME ZONE;

UPDATE identity_follows
SET followed_at = created_at
WHERE status = 'FOLLOWING'
  AND followed_at IS NULL;

CREATE TABLE reward_wallets (
    account_id UUID PRIMARY KEY REFERENCES identity_accounts(id) ON DELETE CASCADE,
    available_balance BIGINT NOT NULL DEFAULT 0,
    lifetime_earned BIGINT NOT NULL DEFAULT 0,
    lifetime_claimed BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_reward_wallets_non_negative CHECK (
        available_balance >= 0
        AND lifetime_earned >= 0
        AND lifetime_claimed >= 0
    )
);

CREATE TABLE reward_achievements (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    reward_type VARCHAR(40) NOT NULL,
    milestone_key VARCHAR(80) NOT NULL,
    required_count BIGINT NOT NULL,
    current_count BIGINT NOT NULL,
    coin_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    achieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    eligible_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_reward_achievement_milestone UNIQUE (account_id, reward_type, milestone_key),
    CONSTRAINT chk_reward_achievement_status CHECK (status IN ('PENDING', 'CLAIMED')),
    CONSTRAINT chk_reward_achievement_counts CHECK (required_count > 0 AND current_count >= 0),
    CONSTRAINT chk_reward_achievement_coins CHECK (coin_amount > 0),
    CONSTRAINT chk_reward_achievement_claimed CHECK (
        (status = 'CLAIMED' AND claimed_at IS NOT NULL)
        OR (status = 'PENDING' AND claimed_at IS NULL)
    )
);

CREATE INDEX idx_reward_achievements_account_timeline
    ON reward_achievements(account_id, achieved_at DESC, id DESC);

CREATE INDEX idx_reward_achievements_claimable
    ON reward_achievements(account_id, status, eligible_at);

CREATE TABLE reward_ledger (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    achievement_id UUID REFERENCES reward_achievements(id) ON DELETE SET NULL,
    transaction_type VARCHAR(30) NOT NULL,
    coin_amount BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_reward_ledger_type CHECK (transaction_type IN ('AWARD_PENDING', 'CLAIM_CREDIT')),
    CONSTRAINT chk_reward_ledger_amount CHECK (coin_amount > 0)
);

CREATE INDEX idx_reward_ledger_account_timeline
    ON reward_ledger(account_id, created_at DESC, id DESC);

CREATE TABLE reward_reel_scrolls (
    account_id UUID NOT NULL REFERENCES identity_accounts(id) ON DELETE CASCADE,
    content_id UUID NOT NULL REFERENCES content_items(id) ON DELETE CASCADE,
    scrolled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (account_id, content_id)
);

CREATE INDEX idx_reward_reel_scrolls_account
    ON reward_reel_scrolls(account_id, scrolled_at DESC);

INSERT INTO reward_wallets(account_id, available_balance, lifetime_earned, lifetime_claimed, created_at, updated_at)
SELECT account.id, 0, 0, 0, now(), now()
FROM identity_accounts account
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO reward_achievements(
    id, account_id, reward_type, milestone_key, required_count, current_count,
    coin_amount, status, achieved_at, eligible_at, created_at, updated_at
)
SELECT
    md5(account.id::text || ':SIGNUP_BONUS:signup')::uuid,
    account.id,
    'SIGNUP_BONUS',
    'signup',
    1,
    1,
    5,
    'PENDING',
    account.created_at,
    account.created_at,
    now(),
    now()
FROM identity_accounts account
ON CONFLICT (account_id, reward_type, milestone_key) DO NOTHING;

WITH ranked_reels AS (
    SELECT
        content.owner_account_id,
        content.published_at,
        row_number() OVER (PARTITION BY content.owner_account_id ORDER BY content.published_at, content.id) AS rn
    FROM content_items content
    WHERE content.content_kind = 'REEL'
      AND content.status = 'PUBLISHED'
      AND content.published_at IS NOT NULL
)
INSERT INTO reward_achievements(
    id, account_id, reward_type, milestone_key, required_count, current_count,
    coin_amount, status, achieved_at, eligible_at, created_at, updated_at
)
SELECT
    md5(owner_account_id::text || ':REEL_UPLOAD:' || rn::text)::uuid,
    owner_account_id,
    'REEL_UPLOAD',
    rn::text,
    rn,
    rn,
    1,
    'PENDING',
    published_at,
    published_at + INTERVAL '7 days',
    now(),
    now()
FROM ranked_reels
WHERE rn % 5 = 0
ON CONFLICT (account_id, reward_type, milestone_key) DO NOTHING;

WITH ranked_followers AS (
    SELECT
        followed_account_id,
        followed_at,
        row_number() OVER (PARTITION BY followed_account_id ORDER BY followed_at, id) AS rn
    FROM identity_follows
    WHERE status = 'FOLLOWING'
      AND followed_at IS NOT NULL
),
follower_milestones(required_count, coin_amount) AS (
    VALUES (1000, 50), (5000, 100), (10000, 500)
)
INSERT INTO reward_achievements(
    id, account_id, reward_type, milestone_key, required_count, current_count,
    coin_amount, status, achieved_at, eligible_at, created_at, updated_at
)
SELECT
    md5(followed_account_id::text || ':FOLLOWER_MILESTONE:' || rn::text)::uuid,
    followed_account_id,
    'FOLLOWER_MILESTONE',
    rn::text,
    rn,
    rn,
    milestone.coin_amount,
    'PENDING',
    followed_at,
    followed_at + INTERVAL '7 days',
    now(),
    now()
FROM ranked_followers follower
JOIN follower_milestones milestone ON milestone.required_count = follower.rn
ON CONFLICT (account_id, reward_type, milestone_key) DO NOTHING;

WITH ranked_content_likes AS (
    SELECT
        content.owner_account_id,
        like_event.created_at,
        row_number() OVER (PARTITION BY content.owner_account_id ORDER BY like_event.created_at, like_event.id) AS rn
    FROM content_likes like_event
    JOIN content_items content ON content.id = like_event.content_id
    WHERE like_event.content_id IS NOT NULL
),
like_milestones(required_count, coin_amount) AS (
    VALUES (200, 1), (500, 2)
)
INSERT INTO reward_achievements(
    id, account_id, reward_type, milestone_key, required_count, current_count,
    coin_amount, status, achieved_at, eligible_at, created_at, updated_at
)
SELECT
    md5(owner_account_id::text || ':CONTENT_LIKE_MILESTONE:' || rn::text)::uuid,
    owner_account_id,
    'CONTENT_LIKE_MILESTONE',
    rn::text,
    rn,
    rn,
    milestone.coin_amount,
    'PENDING',
    created_at,
    created_at + INTERVAL '7 days',
    now(),
    now()
FROM ranked_content_likes like_event
JOIN like_milestones milestone ON milestone.required_count = like_event.rn
ON CONFLICT (account_id, reward_type, milestone_key) DO NOTHING;

WITH ranked_story_likes AS (
    SELECT
        story.owner_account_id,
        like_event.created_at,
        row_number() OVER (PARTITION BY story.owner_account_id ORDER BY like_event.created_at, like_event.id) AS rn
    FROM content_likes like_event
    JOIN content_stories story ON story.id = like_event.story_id
    WHERE like_event.story_id IS NOT NULL
),
like_milestones(required_count, coin_amount) AS (
    VALUES (200, 1), (500, 2)
)
INSERT INTO reward_achievements(
    id, account_id, reward_type, milestone_key, required_count, current_count,
    coin_amount, status, achieved_at, eligible_at, created_at, updated_at
)
SELECT
    md5(owner_account_id::text || ':STORY_LIKE_MILESTONE:' || rn::text)::uuid,
    owner_account_id,
    'STORY_LIKE_MILESTONE',
    rn::text,
    rn,
    rn,
    milestone.coin_amount,
    'PENDING',
    created_at,
    created_at + INTERVAL '7 days',
    now(),
    now()
FROM ranked_story_likes like_event
JOIN like_milestones milestone ON milestone.required_count = like_event.rn
ON CONFLICT (account_id, reward_type, milestone_key) DO NOTHING;

UPDATE reward_wallets wallet
SET lifetime_earned = earned.total,
    updated_at = now()
FROM (
    SELECT account_id, COALESCE(sum(coin_amount), 0) AS total
    FROM reward_achievements
    GROUP BY account_id
) earned
WHERE earned.account_id = wallet.account_id;

INSERT INTO reward_ledger(id, account_id, achievement_id, transaction_type, coin_amount, created_at)
SELECT
    md5('reward-ledger:' || achievement.id::text || ':AWARD_PENDING')::uuid,
    achievement.account_id,
    achievement.id,
    'AWARD_PENDING',
    achievement.coin_amount,
    achievement.created_at
FROM reward_achievements achievement
ON CONFLICT (id) DO NOTHING;
