# Media, moderation, feed, and engagement ER diagram

```mermaid
erDiagram
    identity_accounts ||--o{ content_items : creates
    identity_accounts ||--o{ content_saved_items : saves
    content_items ||--o{ content_saved_items : is_saved
    content_items ||--o{ content_post_media : contains_when_POST
    content_items ||--o| content_transcode_jobs : transcodes_when_REEL
    identity_accounts ||--o{ content_stories : creates
    content_items ||--o{ content_stories : optionally_linked_from
    content_stories ||--o{ content_stories : reshared_by
    content_stories ||--o| content_transcode_jobs : transcodes_when_VIDEO
    content_stories ||--o{ content_story_mentions : tags
    content_stories ||--o{ content_story_views : viewed
    identity_accounts ||--o{ content_story_highlights : owns
    content_story_highlights ||--o{ content_story_highlight_items : contains
    content_stories ||--o{ content_story_highlight_items : collected_in
    content_stories ||--o{ content_likes : receives
    content_stories ||--o{ content_comments : receives
    content_stories ||--o{ trust_content_reports : reported
    identity_accounts ||--o{ content_story_mentions : is_tagged
    identity_accounts ||--o{ content_story_views : views
    content_items ||--o{ trust_content_reports : reported
    content_items ||--o{ content_likes : receives
    content_items ||--o{ content_comments : receives
    identity_accounts ||--o{ content_likes : likes
    identity_accounts ||--o{ content_comments : comments
    content_comments ||--o{ content_comments : has_replies
    content_comments ||--o{ content_comment_mentions : contains
    identity_accounts ||--o{ content_comment_mentions : is_mentioned
    content_comments ||--o{ content_comment_likes : receives
    identity_accounts ||--o{ content_comment_likes : likes
    identity_accounts ||--o{ trust_content_reports : reports
    identity_accounts ||--o{ identity_content_mutes : configures
    identity_accounts ||--o{ identity_content_mutes : is_muted

    content_items {
        UUID id PK
        VARCHAR content_kind
        UUID owner_account_id FK
        VARCHAR caption
        VARCHAR status
        VARCHAR moderation_reason
        TIMESTAMPTZ published_at
        VARCHAR source_object_key
        VARCHAR published_object_key
        VARCHAR thumbnail_object_key
        VARCHAR content_type
        BIGINT source_size_bytes
        INTEGER duration_seconds
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_post_media {
        UUID id PK
        UUID post_id FK
        VARCHAR object_key UK
        VARCHAR content_type
        BIGINT size_bytes
        INTEGER position
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_saved_items {
        UUID id PK
        UUID account_id FK
        UUID content_id FK
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_transcode_jobs {
        UUID id PK
        UUID reel_id UK,FK
        UUID story_id UK,FK
        VARCHAR source_object_key
        VARCHAR output_prefix
        VARCHAR status
        TIMESTAMPTZ claimed_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_stories {
        UUID id PK
        UUID owner_account_id FK
        VARCHAR media_type
        VARCHAR visibility
        VARCHAR status
        VARCHAR caption
        VARCHAR source_object_key
        VARCHAR published_object_key
        VARCHAR thumbnail_object_key
        VARCHAR content_type
        BIGINT source_size_bytes
        INTEGER duration_seconds
        UUID linked_content_id FK
        UUID linked_story_id
        BOOLEAN owns_media
        VARCHAR failure_reason
        VARCHAR moderation_reason
        TIMESTAMPTZ published_at
        TIMESTAMPTZ expires_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_story_mentions {
        UUID id PK
        UUID story_id FK
        UUID mentioned_account_id FK
        VARCHAR handle
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_story_views {
        UUID id PK
        UUID story_id FK
        UUID viewer_account_id FK
        TIMESTAMPTZ viewed_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_story_highlights {
        UUID id PK
        UUID owner_account_id FK
        VARCHAR name
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_story_highlight_items {
        UUID id PK
        UUID highlight_id FK
        UUID story_id FK
        INTEGER position
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    trust_content_reports {
        UUID id PK
        UUID content_id FK
        UUID story_id FK
        UUID reporter_account_id FK
        VARCHAR category
        VARCHAR details
        VARCHAR status
        VARCHAR resolution
        VARCHAR moderator_reason
        TIMESTAMPTZ reviewed_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    identity_content_mutes {
        UUID id PK
        UUID account_id FK
        UUID muted_account_id FK
        VARCHAR content_type
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_likes {
        UUID id PK
        UUID content_id FK
        UUID story_id FK
        UUID account_id FK
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_comments {
        UUID id PK
        UUID content_id FK
        UUID story_id FK
        UUID account_id FK
        UUID thread_root_id FK
        UUID reply_to_comment_id FK
        INTEGER depth
        VARCHAR text
        TIMESTAMPTZ deleted_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_comment_mentions {
        UUID id PK
        UUID comment_id FK
        UUID mentioned_account_id FK
        VARCHAR handle
        INTEGER start_index
        INTEGER end_exclusive
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    content_comment_likes {
        UUID id PK
        UUID comment_id FK
        UUID account_id FK
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
```

`content_items.content_kind` is `POST` or `REEL`. Common ownership, caption, lifecycle, and publication fields live once in this table. Reel-only source/transcode columns are null for posts; post carousel images live in `content_post_media`.

The feed is query-based and has no table in the beta implementation. The unified home feed reads `PUBLISHED` rows from `content_items`, prioritizes the current account and followed accounts, and orders posts and reels together by `published_at` descending. Authenticated feed queries exclude creator/type pairs stored in `identity_content_mutes`; profile and direct-item reads are unchanged. The dedicated reels feed queries only `REEL` rows and applies `REEL` mutes when authenticated.

`content_saved_items` retains one save per account/content pair and never copies media. Posts and reels are queried into separate saved tabs in save-time order. Current visibility is applied when reading: the creator must be active, the content must remain `PUBLISHED`, and the creator must either be public or actively followed by the viewer. A private-content visibility change hides the row without deleting it, allowing the same saved item to reappear when the creator becomes public or the viewer follows again.

Users can like or comment on any visible story and any published post or reel, report permanent content or an active visible story, and like individual comments once per account. A like, comment, or report targets exactly one of `content_id` and `story_id`. Comment replies retain both their top-level thread root and exact reply target while rendering at depth one. Mention ranges use UTF-16 offsets and retain an account ID plus handle snapshot. Comment deletion is soft so existing reply threads remain readable; its likes are removed. Admin moderation can dismiss an individual report or take the target down; a takedown resolves every pending report for that target and removes it from feeds.

Stories are separate from permanent feed content. They may own one uploaded image/video or reuse the first image/published video of an owned post/reel. `expires_at` is exactly 24 hours after `published_at`; live feeds and direct story reads require `expires_at > now`, while the owner archive retains every `PUBLISHED` row and highlights can expose referenced expired rows again. Video stories share `content_transcode_jobs`; exactly one of `reel_id` and `story_id` is populated. Story tags retain an account ID and handle snapshot, while current profile summaries are returned to clients. A unique `(story_id, viewer_account_id)` view row makes `hasSeen` idempotent and supports the owner-only viewer list. Story feeds exclude `STORY`-muted creators, then sort unseen rows first and seen rows second, with each tier ordered by publication time descending.

Highlights are named collections owned by an account. `content_story_highlight_items` stores ordered references, so media is never duplicated and one story may appear in multiple collections. Each collection has unique story and position pairs. Creating or updating a highlight accepts only the owner's `PUBLISHED` stories, including expired stories; deleted, failed, taken-down, or unavailable link-only stories are rejected. Reads ignore story expiry but still apply profile privacy and the story's original `EVERYONE`, `FOLLOWERS`, or `CLOSE_FRIENDS` audience. Expired highlighted stories remain eligible for the existing engagement, viewer, and report operations while referenced by at least one highlight.
