# Frontend prompt: engagement counts and creator settings

Use the backend's new display-count fields wherever the UI shows engagement totals.

## Count display

- For posts, reels, and stories, prefer:
  - `likeCountDisplay` over formatting `likeCount`
  - `commentCountDisplay` over formatting `commentCount`
  - `shareCountDisplay` over formatting `shareCount`
- For comment rows, prefer:
  - `replyCountDisplay` over formatting `replyCount`
  - `likeCountDisplay` over formatting `likeCount`
- For like/unlike responses, update local UI using `likeCount` plus `likeCountDisplay`.
- Display strings follow Indian compact notation:
  - `999`
  - `1K`
  - `1.5K`
  - `1L`
  - `1.2Cr`

Raw numeric fields remain available for sorting, analytics, accessibility labels, and any logic that needs exact values.

## Current backend support

Comments, likes, comment likes, story comments, and share counts exist for posts, reels, and stories.

The backend now supports these create-time engagement toggles for:

- posts
- reels
- stories

- `hideComments`
- `hideLikeCount`
- `hideShareCount`

The backend persists a real share count. Copy-link is not counted. All other share actions count once per authenticated user per post/reel/story.

## Create request fields

On create screens, send these booleans. Omit them when the toggles are off.

```json
{
  "hideComments": true,
  "hideLikeCount": true,
  "hideShareCount": true,
  "shareCount": 1200,
  "shareCountDisplay": "1.2K"
}
```

These fields are accepted by:

- `POST /api/v1/content/uploads` for `type: "POST"`
- `POST /api/v1/content/uploads` for `type: "REEL"`
- `POST /api/v1/stories`

The response fields are present on `PostResponse`, `ReelResponse`, and `StoryResponse`:

```json
{
  "commentsEnabled": false,
  "hideComments": true,
  "hideLikeCount": true,
  "hideShareCount": true
}
```

`commentsEnabled` is kept for compatibility and is the inverse of `hideComments`.

Frontend behavior:

- If `hideComments` is `true` or `commentsEnabled` is `false`, hide/disable add-comment and reply entry points, and show a passive "Comments are off" state on the comments screen.
- Existing comments are still readable.
- If the frontend still attempts to create a comment or reply, the API returns `403` with reason `Comments are turned off`.
- If `hideLikeCount` is `true`, hide the public like total but keep the viewer's own liked/unliked state.
- If `hideShareCount` is `true`, hide the public share total but keep the share action available wherever the product allows sharing.
- For visible share totals, render `shareCountDisplay`.

## Share API

When the user performs a share from the normal share sheet, call:

```http
POST /api/v1/content/{contentId}/shares
POST /api/v1/stories/{storyId}/shares
Authorization: Bearer <accessToken>
```

Request body:

```json
{ "channel": "OTHER" }
```

Use these channels:

- `COPY_LINK` for copy-link actions. Backend returns a response but does not increment the count.
- `DIRECT_MESSAGE` only if frontend records a DM share outside the chat message API. Normal chat `CONTENT_SHARE` messages are already counted automatically by backend.
- `STORY` only if frontend records a story share outside story creation. Normal “share post/reel/story to story” creation is already counted automatically by backend.
- `EXTERNAL` for external app shares.
- `OTHER` for generic in-app share actions.

Response:

```json
{
  "targetId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "targetType": "CONTENT",
  "shared": true,
  "shareCount": 1200,
  "shareCountDisplay": "1.2K"
}
```

Update local UI from `shareCount` and `shareCountDisplay`. Because the backend counts only one share per user per target, repeated shares by the same user will keep the same count.

## Share-list API

When user taps/opens the share count/list, call:

```http
GET /api/v1/content/{contentId}/shares?limit=20&cursor={nextCursor}
GET /api/v1/stories/{storyId}/shares?limit=20&cursor={nextCursor}
Authorization: Bearer <optional-accessToken>
```

Response is `ProfileSummaryPageResponse`:

```json
{
  "items": [
    {
      "accountId": "550e8400-e29b-41d4-a716-446655440000",
      "handle": "rajat_01",
      "displayName": "Rajat Singh",
      "avatarUrl": "https://s3.example.com/temporary-avatar",
      "viewerState": null
    }
  ],
  "nextCursor": "opaque-cursor-or-null"
}
```

Render only avatar, display name/username, and handle if that is all the UI needs. Pass `nextCursor` unchanged for pagination.
