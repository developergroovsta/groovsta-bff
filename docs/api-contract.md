# Groovsta API contract

Source contract for frontend, mobile, transcode-worker, and moderation integrations.

- Contract date: 2026-08-02
- API version: `v1`
- Local base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Default request/response media type: `application/json`

The Kotlin controllers and DTOs are the implementation source of truth. Update this document whenever an endpoint, DTO, enum, validation rule, or status code changes.

## Conventions

### Authentication

Protected mobile endpoints require:

```http
Authorization: Bearer <accessToken>
```

Endpoints documented with optional bearer authentication accept the same header but also allow it to be omitted. If the `Authorization` header is present, it must contain a valid, unexpired bearer token; malformed, invalid, expired, or revoked supplied credentials return `401` rather than falling back to anonymous behavior.

Signup, password login, and Google authentication require device metadata:

```http
X-Device-Id: android-550e8400
X-Platform: ANDROID
X-App-Version: 1.0.0
User-Agent: Groovsta/1.0.0
```

`X-Platform` values are `ANDROID`, `IOS`, `WEB`, and `UNKNOWN`. Unknown values are normalized to `UNKNOWN`.

Internal transcode endpoints require:

```http
X-Transcode-Secret: <worker-secret>
```

Moderation endpoints require:

```http
X-Moderation-Admin-Key: <admin-key>
```

The worker and admin headers are beta contracts and must not be exposed in the consumer mobile application.

### Using authenticated endpoints in Swagger UI

1. Open `http://localhost:8080/swagger-ui.html` after restarting the application.
2. Execute `POST /api/v1/auth/signup` or `POST /api/v1/auth/login` with the required device headers.
3. Copy only `session.accessToken` from the response.
4. Click **Authorize** at the top of Swagger UI or the lock icon beside a protected operation.
5. Paste the token without adding `Bearer ` and confirm authorization.
6. Open a protected endpoint and click **Execute**. Swagger sends `Authorization: Bearer <accessToken>` automatically.

If **Authorize** is not visible after deployment, open `/v3/api-docs` and confirm that `components.securitySchemes.bearerAuth` exists. A stale Swagger page may need a hard refresh after restarting the backend.

### Data formats

- UUIDs are JSON strings such as `550e8400-e29b-41d4-a716-446655440000`.
- Times are UTC ISO-8601 strings such as `2026-07-14T09:30:00Z`.
- Phone numbers use E.164, for example `+919876543210`.
- Nullable fields may be `null` or absent in requests only when the request schema permits it.
- Presigned S3 URLs are temporary. Never persist them as permanent media identifiers.
- Count response fields keep raw numeric values and may also include a sibling display string. Display strings use compact Indian notation: `1K` at 1,000, up to one decimal place, `L` at lakh, and `Cr` at crore. Examples: `999`, `1K`, `1.5K`, `1L`, `1.2Cr`.

### Standard error response

Every controller uses the same error shape:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "One or more fields are invalid",
  "timestamp": "2026-07-14T09:30:00Z",
  "fields": {
    "email": "must be a well-formed email address"
  }
}
```

Common statuses and codes:

| HTTP | Typical code | Meaning |
|---:|---|---|
| `400` | `VALIDATION_FAILED` or `INVALID_REQUEST` | Invalid JSON, headers, parameters, state transition, or field validation |
| `401` | `INVALID_CREDENTIALS`, `INVALID_EXTERNAL_IDENTITY`, or `401_UNAUTHORIZED` | Missing/expired bearer token, invalid Google identity, or invalid internal/admin secret |
| `403` | `403_FORBIDDEN` | Authenticated caller is not allowed to perform the requested action |
| `404` | `NOT_FOUND` or `404_NOT_FOUND` | Requested resource is unavailable or hidden by access rules |
| `409` | `CONFLICT` | Duplicate account/handle or already-verified contact |
| `410` | `410_GONE` | A call invitation expired before it was accepted |
| `422` | `INVALID_VERIFICATION` or `INVALID_EXTERNAL_REGISTRATION` | Missing, expired, invalid, consumed, or wrong-purpose verification/registration token |
| `503` | `EMAIL_DELIVERY_FAILED` | Verification email delivery is temporarily unavailable |
| `500` | `INTERNAL_SERVER_ERROR` | Unexpected backend failure |

Frontend clients should branch primarily on HTTP status and `code`, not human-readable `message`.

## Shared response schemas

### ProfileResponse

```json
{
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "handle": "rajat_01",
  "displayName": "Rajat Singh",
  "bio": "Music and dance creator",
  "avatarUrl": "https://s3.example.com/presigned-avatar",
  "isPublic": true,
  "followerCount": 125,
  "followingCount": 42,
  "postCount": 18,
  "createdAt": "2026-07-14T09:00:00Z",
  "viewerState": {
    "relationship": "FOLLOWING",
    "followsViewer": false,
    "canViewContent": true,
    "postsMuted": false,
    "reelsMuted": false,
    "storiesMuted": true
  }
}
```

`avatarUrl` is nullable and temporary. `isPublic` defaults to `true` for new and migrated profiles and controls whether unauthenticated and non-following users can see the profile's published content. `postCount` counts only published posts and intentionally excludes reels. `viewerState` is nullable and is `null` for anonymous requests. Relationship values are `SELF`, `NONE`, `FOLLOWING`, and `REQUESTED`. `REQUESTED` means the authenticated viewer has a pending request to follow a private profile.

For an authenticated viewer, `followsViewer` is `true` when the target account actively follows the viewer. `canViewContent` is `true` when the profile is public, the relationship is `SELF`, or the relationship is `FOLLOWING`. Follow rows involving a non-`ACTIVE` account do not produce `FOLLOWING` or `followsViewer: true`.

`postsMuted`, `reelsMuted`, and `storiesMuted` expose the authenticated viewer's independent creator mute settings. They are always `false` for `SELF`; anonymous profile responses have `viewerState: null`. Profile search resolves mute state in the same batch as the other viewer-specific fields.

### AccountResponse

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "rajat@example.com",
  "phoneNumber": "+919876543210",
  "status": "ACTIVE",
  "emailVerified": true,
  "phoneVerified": true,
  "profile": {
    "accountId": "550e8400-e29b-41d4-a716-446655440000",
    "handle": "rajat_01",
    "displayName": "Rajat Singh",
    "bio": null,
    "avatarUrl": null,
    "isPublic": true,
    "followerCount": 0,
    "followingCount": 0,
    "postCount": 0,
    "createdAt": "2026-07-14T09:00:00Z",
    "viewerState": {
      "relationship": "SELF",
      "followsViewer": false,
      "canViewContent": true,
      "postsMuted": false,
      "reelsMuted": false,
      "storiesMuted": false
    }
  }
}
```

Account statuses: `RESTRICTED`, `ACTIVE`, `SUSPENDED`.

### VerificationChallengeResponse

```json
{
  "channel": "EMAIL",
  "destination": "r***@example.com",
  "expiresAt": "2026-07-14T09:45:00Z"
}
```

Channels: `EMAIL`, `PHONE`. The destination is masked.

### AuthResponse

```json
{
  "account": { "id": "550e8400-e29b-41d4-a716-446655440000", "email": "rajat@example.com", "phoneNumber": "+919876543210", "status": "RESTRICTED", "emailVerified": false, "phoneVerified": false, "profile": { "accountId": "550e8400-e29b-41d4-a716-446655440000", "handle": "rajat_01", "displayName": "Rajat Singh", "bio": null, "avatarUrl": null, "isPublic": true, "followerCount": 0, "followingCount": 0, "postCount": 0, "createdAt": "2026-07-14T09:00:00Z", "viewerState": { "relationship": "SELF", "followsViewer": false, "canViewContent": true, "postsMuted": false, "reelsMuted": false, "storiesMuted": false } } },
  "session": {
    "accessToken": "raw-token-returned-only-once",
    "tokenType": "Bearer",
    "expiresAt": "2026-08-13T09:00:00Z"
  },
  "nextVerification": {
    "channel": "EMAIL",
    "destination": "r***@example.com",
    "expiresAt": "2026-07-14T09:15:00Z"
  }
}
```

`nextVerification` is nullable. Store `session.accessToken` securely and send it as a bearer token.

### ReelResponse

```json
{
  "id": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "ownerAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "caption": "My first Groovsta reel",
  "status": "PUBLISHED",
  "playbackUrl": "https://s3.example.com/presigned-video",
  "thumbnailUrl": "https://s3.example.com/presigned-thumbnail",
  "durationSeconds": 28,
  "taggedAccounts": [
    {
      "accountId": "3e050d27-7567-45c0-9450-f8b01beb03c8",
      "handle": "friend_01",
      "displayName": "Friend One",
      "avatarUrl": "https://s3.example.com/temporary-avatar",
      "viewerState": null
    }
  ],
  "location": {
    "id": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
    "provider": "OLA",
    "providerPlaceId": "ola-platform:5000039498427",
    "name": "Koramangala",
    "formattedAddress": "Koramangala, Bengaluru, Karnataka, India",
    "latitude": 12.9352,
    "longitude": 77.6245,
    "city": "Bengaluru",
    "state": "Karnataka",
    "country": "India",
    "postalCode": null
  },
  "canShareToStory": true,
  "commentsEnabled": true,
  "hideComments": false,
  "hideLikeCount": false,
  "hideShareCount": false,
  "likeCount": 10,
  "likeCountDisplay": "10",
  "liked": true,
  "commentCount": 3,
  "commentCountDisplay": "3",
  "shareCount": 1200,
  "shareCountDisplay": "1.2K",
  "publishedAt": "2026-07-14T10:00:00Z",
  "createdAt": "2026-07-14T09:40:00Z",
  "moderationReason": null,
  "saved": false
}
```

Reel statuses: `UPLOADING`, `TRANSCODING`, `READY_TO_PUBLISH`, `PUBLISHED`, `TAKEN_DOWN`, `FAILED`.

Media URLs, duration, and publication time are nullable before their corresponding lifecycle steps.

### PostResponse

```json
{
  "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
  "ownerAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "caption": "Photos from tonight",
  "status": "PUBLISHED",
  "media": [
    {
      "id": "d3efb38c-d661-47b5-a9d2-a374b381a406",
      "position": 0,
      "contentType": "image/jpeg",
      "imageUrl": "https://s3.example.com/presigned-image-1"
    },
    {
      "id": "35c4b9e1-9eac-4c66-8987-826a83f2b523",
      "position": 1,
      "contentType": "image/webp",
      "imageUrl": "https://s3.example.com/presigned-image-2"
    }
  ],
  "taggedAccounts": [
    {
      "accountId": "3e050d27-7567-45c0-9450-f8b01beb03c8",
      "handle": "friend_01",
      "displayName": "Friend One",
      "avatarUrl": "https://s3.example.com/temporary-avatar",
      "viewerState": null
    }
  ],
  "location": {
    "id": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
    "provider": "OLA",
    "providerPlaceId": "ola-platform:5000039498427",
    "name": "Koramangala",
    "formattedAddress": "Koramangala, Bengaluru, Karnataka, India",
    "latitude": 12.9352,
    "longitude": 77.6245,
    "city": "Bengaluru",
    "state": "Karnataka",
    "country": "India",
    "postalCode": null
  },
  "canShareToStory": true,
  "commentsEnabled": true,
  "hideComments": false,
  "hideLikeCount": false,
  "hideShareCount": false,
  "likeCount": 18,
  "likeCountDisplay": "18",
  "liked": false,
  "commentCount": 4,
  "commentCountDisplay": "4",
  "shareCount": 1200,
  "shareCountDisplay": "1.2K",
  "publishedAt": "2026-07-15T10:00:00Z",
  "createdAt": "2026-07-15T09:55:00Z",
  "saved": false
}
```

Post statuses: `UPLOADING`, `PUBLISHED`, `TAKEN_DOWN`. The `media` array is ordered by `position`. Each `imageUrl` is temporary and must not be stored as a permanent identifier. `publishedAt` is nullable while the owner is retrieving an uploading post.

`location` is nullable on `PostResponse`, `ReelResponse`, `ContentFeedItemResponse.post`, `ContentFeedItemResponse.reel`, and `StoryResponse`. When present, use `location.id` for navigation and later content queries. `providerPlaceId` is provider metadata only; do not store it as the frontend's primary identifier.

`liked` is a non-null viewer-specific boolean on `PostResponse`, `ReelResponse`, and `StoryResponse`. It is `true` only when the authenticated viewer has liked that item and `false` for other viewers and anonymous requests. Feed pages resolve it with one batched query. `saved` remains `true` or `false` with a valid bearer token and `null` anonymously.

`likeCountDisplay`, `commentCountDisplay`, and `shareCountDisplay` are present beside the raw `likeCount`, `commentCount`, and `shareCount` on `PostResponse`, `ReelResponse`, and `StoryResponse`.

`hideComments`, `hideLikeCount`, and `hideShareCount` are creator settings on `PostResponse`, `ReelResponse`, and `StoryResponse`. `commentsEnabled` is retained for compatibility and is the inverse of `hideComments`. When comments are hidden/disabled, existing comments remain readable but creating a new top-level comment or reply returns `403`. When `hideLikeCount` or `hideShareCount` is `true`, clients must hide those public counts. Raw numeric count fields remain in the response for backward compatibility.

`shareCount` counts one share per user per target. It is incremented by share actions other than copy-link: direct-message content shares, sharing post/reel to story, resharing a story, and explicit share records through the engagement endpoints. Copy-link actions must use channel `COPY_LINK`; they return a response but do not increment the stored count.

## Endpoint summary

| Audience | Method | Path | Auth | Success response |
|---|---|---|---|---|
| Public | `GET` | `/api/health` | None | `200` health object |
| Public | `POST` | `/api/v1/auth/signup` | Device headers | `201` `AuthResponse` |
| Public | `POST` | `/api/v1/auth/login` | Device headers | `200` `AuthResponse` |
| Public | `POST` | `/api/v1/auth/google` | Device headers | `200` `GoogleAuthenticationResponse` |
| Public | `POST` | `/api/v1/auth/google/signup` | Device headers | `201` `AuthResponse` |
| Public | `POST` | `/api/v1/auth/forgot-password` | None | `200` `VerificationChallengeResponse` |
| Public | `POST` | `/api/v1/auth/reset-password` | None | `204` |
| Mobile | `POST` | `/api/v1/auth/logout` | Bearer | `204` |
| Mobile | `POST` | `/api/v1/auth/email-verifications` | Bearer | `200` `VerificationChallengeResponse` |
| Mobile | `POST` | `/api/v1/auth/email-verifications/confirm` | Bearer | `200` `AccountResponse` |
| Mobile | `POST` | `/api/v1/auth/phone-verifications` | Bearer | `200` `VerificationChallengeResponse` |
| Mobile | `POST` | `/api/v1/auth/phone-verifications/confirm` | Bearer | `200` `AccountResponse` |
| Mobile | `GET` | `/api/v1/me` | Bearer | `200` `ProfileResponse` |
| Mobile | `PATCH` | `/api/v1/me` | Bearer | `200` `ProfileResponse` |
| Mobile | `POST` | `/api/v1/me/avatar/uploads` | Bearer | `200` `UploadUrlResponse` |
| Mobile | `POST` | `/api/v1/me/avatar/uploads/complete` | Bearer | `200` `ProfileResponse` |
| Public/mobile | `GET` | `/api/v1/profiles/{handle}` | Optional bearer | `200` `ProfileResponse` |
| Public/mobile | `GET` | `/api/v1/profiles/search` | Optional bearer | `200` `ProfileSearchResponse` |
| Public/mobile | `GET` | `/api/v1/profiles/{handle}/followers` | Optional bearer | `200` `ProfileSummaryPageResponse` |
| Public/mobile | `GET` | `/api/v1/profiles/{handle}/following` | Optional bearer | `200` `ProfileSummaryPageResponse` |
| Mobile | `POST` | `/api/v1/profiles/{handle}/follow` | Bearer | `200` `FollowResponse` |
| Mobile | `DELETE` | `/api/v1/profiles/{handle}/follow` | Bearer | `200` `FollowResponse` |
| Mobile | `POST` | `/api/v1/follow-requests/{requestId}/accept` | Bearer, recipient only | `200` `FollowRequestDecisionResponse` |
| Mobile | `DELETE` | `/api/v1/follow-requests/{requestId}` | Bearer, recipient only | `204` |
| Mobile | `GET` | `/api/v1/notifications` | Bearer | `200` `NotificationPageResponse` |
| Mobile | `GET` | `/api/v1/notifications/unread-count` | Bearer | `200` `UnreadNotificationCountResponse` |
| Mobile | `POST` | `/api/v1/notifications/{notificationId}/read` | Bearer | `200` `NotificationResponse` |
| Mobile | `POST` | `/api/v1/notifications/read-all` | Bearer | `204` |
| Mobile | `PUT` | `/api/v1/notifications/push-token` | Bearer + device ID | `200` `PushRegistrationResponse` |
| Mobile | `DELETE` | `/api/v1/notifications/push-token` | Bearer + device ID | `204` |
| Mobile | `GET` | `/api/v1/me/close-friends` | Bearer | `200` `CloseFriendsResponse` |
| Mobile | `PUT` | `/api/v1/me/close-friends/{handle}` | Bearer | `200` `ProfileSummaryResponse` |
| Mobile | `DELETE` | `/api/v1/me/close-friends/{handle}` | Bearer | `204` |
| Mobile | `PUT` | `/api/v1/me/mutes/{handle}/{contentType}` | Bearer | `200` `ContentMuteResponse` |
| Mobile | `DELETE` | `/api/v1/me/mutes/{handle}/{contentType}` | Bearer | `200` `ContentMuteResponse` |
| Mobile | `POST` | `/api/v1/content/uploads` | Bearer | `200` `ContentUploadResponse` |
| Mobile | `POST` | `/api/v1/content/{contentId}/uploads/complete` | Bearer | `200` `ContentResponse` |
| Public/owner | `GET` | `/api/v1/content/{contentId}` | Optional bearer | `200` `ContentResponse` |
| Mobile | `POST` | `/api/v1/content/{contentId}/publish` | Bearer | `200` `ContentResponse` |
| Mobile | `POST` | `/api/v1/content/{contentId}/saves` | Bearer | `200` `SavedContentResponse` |
| Mobile | `DELETE` | `/api/v1/content/{contentId}/saves` | Bearer | `200` `SavedContentResponse` |
| Mobile | `GET` | `/api/v1/me/saved/posts` | Bearer | `200` `SavedContentPageResponse` |
| Mobile | `GET` | `/api/v1/me/saved/reels` | Bearer | `200` `SavedContentPageResponse` |
| Mobile | `GET` | `/api/v1/locations/search` | Bearer | `200` `LocationSearchResponse` |
| Mobile | `POST` | `/api/v1/locations/resolve` | Bearer | `200` `LocationSummaryResponse` |
| Mobile | `GET` | `/api/v1/locations/reverse` | Bearer | `200` `LocationSummaryResponse` |
| Public/mobile | `GET` | `/api/v1/locations/{locationId}` | None | `200` `LocationSummaryResponse` |
| Public/mobile | `GET` | `/api/v1/locations/{locationId}/content` | Optional bearer | `200` `LocationContentPageResponse` |
| Public/mobile | `GET` | `/api/v1/locations/{locationId}/stories` | Optional bearer | `200` `StoryPageResponse` |
| Mobile | `GET` | `/api/v1/chat/conversations` | Bearer | `200` `ChatConversationPageResponse` |
| Mobile | `GET` | `/api/v1/chat/conversations/{conversationId}/messages` | Bearer | `200` `ChatMessagePageResponse` |
| Mobile | `POST` | `/api/v1/chat/messages` | Bearer | `201` `ChatMessageResponse` |
| Mobile | `PATCH` | `/api/v1/chat/messages/{messageId}` | Bearer, sender only | `200` `ChatMessageResponse` |
| Mobile | `DELETE` | `/api/v1/chat/messages/{messageId}` | Bearer, sender only | `200` `ChatMessageResponse` |
| Mobile | `PUT` | `/api/v1/chat/messages/{messageId}/reaction` | Bearer | `200` `ChatMessageResponse` |
| Mobile | `DELETE` | `/api/v1/chat/messages/{messageId}/reaction` | Bearer | `200` `ChatMessageResponse` |
| Mobile | `POST` | `/api/v1/chat/conversations/{conversationId}/delivered` | Bearer | `200` `ChatDeliveryReceiptResponse` |
| Mobile | `POST` | `/api/v1/chat/conversations/{conversationId}/read` | Bearer | `200` `ChatReadReceiptResponse` |
| Mobile | `GET` | `/api/v1/chat/presence/{accountId}` | Bearer | `200` `ChatPresenceResponse` |
| Mobile | `POST` | `/api/v1/calls` | Bearer | `201` `CallResponse` |
| Mobile | `GET` | `/api/v1/calls` | Bearer | `200` `CallPageResponse` |
| Mobile | `GET` | `/api/v1/calls/active` | Bearer | `200` list of `CallResponse` |
| Mobile | `GET` | `/api/v1/calls/ice-configuration` | Bearer | `200` `IceConfigurationResponse` |
| Mobile | `GET` | `/api/v1/calls/{callId}` | Bearer, participant only | `200` `CallResponse` |
| Mobile | `POST` | `/api/v1/calls/{callId}/accept` | Bearer, invitee only | `200` `CallResponse` |
| Mobile | `POST` | `/api/v1/calls/{callId}/decline` | Bearer, invitee only | `200` `CallResponse` |
| Mobile | `POST` | `/api/v1/calls/{callId}/cancel` | Bearer, initiator only | `200` `CallResponse` |
| Mobile | `POST` | `/api/v1/calls/{callId}/leave` | Bearer, joined participant | `200` `CallResponse` |
| Mobile | `POST` | `/api/v1/calls/{callId}/end` | Bearer, current host | `200` `CallResponse` |
| Mobile | `POST` | `/api/v1/calls/{callId}/participants` | Bearer, current host | `200` `CallResponse` |
| Mobile | `PATCH` | `/api/v1/calls/{callId}/media-state` | Bearer, joined participant | `200` `CallResponse` |
| Mobile | `POST` | `/api/v1/calls/heartbeat` | Bearer, joined participant | `204` |
| Mobile | `POST` | `/api/v1/content/{contentId}/reports` | Bearer | `200` `ContentReportResponse` |
| Public/mobile | `GET` | `/api/v1/feeds/reels` | Optional bearer | `200` `FeedResponse` |
| Public/mobile | `GET` | `/api/v1/feeds/home` | Optional bearer | `200` `HomeFeedResponse` |
| Public/mobile | `GET` | `/api/v1/profiles/{handle}/posts` | Optional bearer | `200` `PostFeedResponse` |
| Public/mobile | `GET` | `/api/v1/profiles/{handle}/reels` | Optional bearer | `200` `FeedResponse` |
| Mobile | `POST` | `/api/v1/stories` | Bearer | `200` `StoryCreateResponse` |
| Mobile | `POST` | `/api/v1/stories/{storyId}/uploads/complete` | Bearer | `200` `StoryResponse` |
| Public/mobile | `GET` | `/api/v1/stories/{storyId}` | Optional bearer | `200` `StoryResponse` |
| Mobile | `POST` | `/api/v1/stories/{storyId}/views` | Bearer | `200` `StorySeenResponse` |
| Mobile | `GET` | `/api/v1/stories/{storyId}/views` | Bearer, owner only | `200` `ProfileSummaryPageResponse` |
| Public/mobile | `GET` | `/api/v1/stories/{storyId}/likes` | Optional bearer | `200` `ProfileSummaryPageResponse` |
| Mobile | `POST` | `/api/v1/stories/{storyId}/likes` | Bearer | `200` `StoryLikeResponse` |
| Mobile | `DELETE` | `/api/v1/stories/{storyId}/likes` | Bearer | `200` `StoryLikeResponse` |
| Public/mobile | `GET` | `/api/v1/stories/{storyId}/shares` | Optional bearer | `200` `ProfileSummaryPageResponse` |
| Mobile | `POST` | `/api/v1/stories/{storyId}/shares` | Bearer | `200` `ShareResponse` |
| Public/mobile | `GET` | `/api/v1/stories/{storyId}/comments` | Optional bearer | `200` `CommentPageResponse` |
| Mobile | `POST` | `/api/v1/stories/{storyId}/comments` | Bearer | `200` `CommentResponse` |
| Public/mobile | `GET` | `/api/v1/stories/{storyId}/comments/{commentId}/replies` | Optional bearer | `200` `CommentPageResponse` |
| Mobile | `POST` | `/api/v1/stories/{storyId}/comments/{commentId}/likes` | Bearer | `200` `CommentLikeResponse` |
| Mobile | `DELETE` | `/api/v1/stories/{storyId}/comments/{commentId}/likes` | Bearer | `200` `CommentLikeResponse` |
| Mobile | `DELETE` | `/api/v1/stories/{storyId}/comments/{commentId}` | Bearer | `204` |
| Mobile | `DELETE` | `/api/v1/stories/{storyId}` | Bearer | `204` |
| Mobile | `POST` | `/api/v1/stories/{storyId}/reports` | Bearer | `200` `StoryReportResponse` |
| Public/mobile | `GET` | `/api/v1/feeds/stories` | Optional bearer | `200` `StoryPageResponse` |
| Public/mobile | `GET` | `/api/v1/profiles/{handle}/stories` | Optional bearer | `200` `StoryPageResponse` |
| Mobile | `GET` | `/api/v1/me/stories/archive` | Bearer | `200` `StoryPageResponse` |
| Mobile | `POST` | `/api/v1/highlights` | Bearer | `200` `StoryHighlightResponse` |
| Mobile | `PUT` | `/api/v1/highlights/{highlightId}` | Bearer, owner only | `200` `StoryHighlightResponse` |
| Mobile | `DELETE` | `/api/v1/highlights/{highlightId}` | Bearer, owner only | `204` |
| Public/mobile | `GET` | `/api/v1/profiles/{handle}/highlights` | Optional bearer | `200` `StoryHighlightPageResponse` |
| Public/mobile | `GET` | `/api/v1/highlights/{highlightId}` | Optional bearer | `200` `StoryHighlightResponse` |
| Public/mobile | `GET` | `/api/v1/content/{contentId}/likes` | Optional bearer | `200` `ProfileSummaryPageResponse` |
| Mobile | `POST` | `/api/v1/content/{contentId}/likes` | Bearer | `200` `LikeResponse` |
| Mobile | `DELETE` | `/api/v1/content/{contentId}/likes` | Bearer | `200` `LikeResponse` |
| Public/mobile | `GET` | `/api/v1/content/{contentId}/shares` | Optional bearer | `200` `ProfileSummaryPageResponse` |
| Mobile | `POST` | `/api/v1/content/{contentId}/shares` | Bearer | `200` `ShareResponse` |
| Mobile | `POST` | `/api/v1/content/{contentId}/comments` | Bearer | `200` `CommentResponse` |
| Public/mobile | `GET` | `/api/v1/content/{contentId}/comments` | Optional bearer | `200` `CommentPageResponse` |
| Public/mobile | `GET` | `/api/v1/content/{contentId}/comments/{commentId}/replies` | Optional bearer | `200` `CommentPageResponse` |
| Mobile | `POST` | `/api/v1/content/{contentId}/comments/{commentId}/likes` | Bearer | `200` `CommentLikeResponse` |
| Mobile | `DELETE` | `/api/v1/content/{contentId}/comments/{commentId}/likes` | Bearer | `200` `CommentLikeResponse` |
| Mobile | `DELETE` | `/api/v1/content/{contentId}/comments/{commentId}` | Bearer | `200` empty body |
| Worker | `POST` | `/api/v1/internal/transcode/jobs/claim` | Transcode secret | `200` job or `204` |
| Worker | `POST` | `/api/v1/internal/transcode/jobs/{jobId}/complete` | Transcode secret | `200` empty body |
| Worker | `POST` | `/api/v1/internal/transcode/jobs/{jobId}/fail` | Transcode secret | `200` empty body |
| Worker | `POST` | `/api/v1/internal/transcode/reels/{reelId}/complete` | Transcode secret | `200` empty body |
| Worker | `POST` | `/api/v1/internal/transcode/reels/{reelId}/fail` | Transcode secret | `200` empty body |
| Admin | `GET` | `/api/v1/admin/moderation/reports` | Admin key | `200` `ContentReportAdminResponse[]` |
| Admin | `POST` | `/api/v1/admin/moderation/reports/{reportId}/decision` | Admin key | `200` `ContentReportAdminResponse` |

## Authentication endpoints

### Create account

```http
POST /api/v1/auth/signup
X-Device-Id: android-550e8400
X-Platform: ANDROID
X-App-Version: 1.0.0
Content-Type: application/json
```

```json
{
  "email": "rajat@example.com",
  "phoneNumber": "+919876543210",
  "password": "CorrectHorse123",
  "handle": "rajat_01",
  "displayName": "Rajat Singh",
  "adultDeclaration": true
}
```

Rules:

- At least one of `email` or `phoneNumber` is required; both are allowed.
- `email`: valid email, maximum 320 characters.
- `phoneNumber`: E.164.
- `password`: 8–128 characters.
- `handle`: 3–30 ASCII letters, digits, or underscores; stored lowercase.
- `displayName`: nonblank, maximum 80 characters.
- `adultDeclaration`: must be `true`.
- Email, phone, and handle conflicts return `409`.

Supported contact combinations:

```json
{ "email": "rajat@example.com", "phoneNumber": null }
```

```json
{ "email": null, "phoneNumber": "+919876543210" }
```

```json
{ "email": "rajat@example.com", "phoneNumber": "+919876543210" }
```

These snippets show only the contact fields; every signup request still requires `password`, `handle`, `displayName`, and `adultDeclaration`.

Returns `201 AuthResponse`. If email is supplied, `nextVerification` is email; otherwise it is phone. The account remains `RESTRICTED` until both an email and phone number have been attached and verified; only then does it become `ACTIVE` and qualify for points-related actions.

### Login

```http
POST /api/v1/auth/login
```

```json
{
  "email": "rajat@example.com",
  "phoneNumber": null,
  "password": "CorrectHorse123"
}
```

Exactly one of `email` or `phoneNumber` is required. Device headers are the same as signup. Returns `200 AuthResponse`; `nextVerification` is `null`.

### Sign in with Google

The frontend signs in with Google through Firebase Authentication, obtains a Firebase ID token, and exchanges it for a normal Groovsta session. Do not send a Google access token, authorization code, Firebase custom token, email, or Google profile object to this endpoint.

```http
POST /api/v1/auth/google
X-Device-Id: android-550e8400
X-Platform: ANDROID
X-App-Version: 1.0.0
Content-Type: application/json
```

```json
{
  "idToken": "<firebase-id-token>"
}
```

The backend verifies the token signature, Firebase project, expiry, revocation, `google.com` sign-in provider, and verified email. The token is never stored.

For a linked identity or an existing Groovsta account with the same verified email, the response is:

```json
{
  "status": "AUTHENTICATED",
  "account": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "rajat@gmail.com",
    "phoneNumber": null,
    "status": "RESTRICTED",
    "emailVerified": true,
    "phoneVerified": false,
    "profile": {
      "accountId": "550e8400-e29b-41d4-a716-446655440000",
      "handle": "rajat_01",
      "displayName": "Rajat Singh",
      "bio": null,
      "avatarUrl": null,
      "isPublic": true,
      "followerCount": 0,
      "followingCount": 0,
      "postCount": 0,
      "createdAt": "2026-08-02T09:00:00Z",
      "viewerState": {
        "relationship": "SELF",
        "followsViewer": false,
        "canViewContent": true,
        "postsMuted": false,
        "reelsMuted": false,
        "storiesMuted": false
      }
    }
  },
  "session": {
    "accessToken": "raw-groovsta-token-returned-only-once",
    "tokenType": "Bearer",
    "expiresAt": "2026-09-01T09:00:00Z"
  },
  "nextVerification": null,
  "registrationToken": null,
  "registrationExpiresAt": null,
  "email": null,
  "suggestedDisplayName": null
}
```

Store `session.accessToken` exactly like password login. Firebase ID tokens are not accepted as bearer credentials by other Groovsta endpoints.

For a first-time user, the response is:

```json
{
  "status": "ONBOARDING_REQUIRED",
  "account": null,
  "session": null,
  "nextVerification": null,
  "registrationToken": "short-lived-one-time-token",
  "registrationExpiresAt": "2026-08-02T17:10:00Z",
  "email": "rajat@gmail.com",
  "suggestedDisplayName": "Rajat Singh"
}
```

`registrationToken` expires after 10 minutes by default. It must be held only in memory, must not be logged, and is invalid after successful completion or when a newer Google authentication attempt replaces it.

Invalid, expired, revoked, wrong-project, non-Google, or email-unverified Firebase tokens return `401 INVALID_EXTERNAL_IDENTITY`.

### Complete Google signup

```http
POST /api/v1/auth/google/signup
X-Device-Id: android-550e8400
X-Platform: ANDROID
X-App-Version: 1.0.0
Content-Type: application/json
```

```json
{
  "registrationToken": "short-lived-one-time-token",
  "handle": "rajat_01",
  "displayName": "Rajat Singh",
  "adultDeclaration": true
}
```

Rules:

- `registrationToken`: required, one-time, maximum 128 characters.
- `handle`: same validation and uniqueness rules as normal signup.
- `displayName`: nonblank, maximum 80 characters.
- `adultDeclaration`: must be `true`.
- The verified Google email becomes the account email and is immediately marked verified.
- No Google avatar URL is persisted. Avatar creation continues through the S3 avatar-upload endpoints.
- If the email became associated with an existing account between initiation and completion, that account is linked instead of creating a duplicate.

Returns `201 AuthResponse`. A Google-only account initially has no local password. A successful verified-email password-reset flow can set one later. The account remains `RESTRICTED` until a phone number is attached and verified, after which both contacts are verified and the account becomes `ACTIVE`.

Expired, consumed, superseded, or unknown registration tokens return `422 INVALID_EXTERNAL_REGISTRATION`. Handle conflicts return `409 CONFLICT`.

### Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer <accessToken>
```

Returns `204`. The submitted session token becomes unusable.

### Request email verification

```http
POST /api/v1/auth/email-verifications
Authorization: Bearer <accessToken>
```

For an account that already has an unverified email, omit the body or send `{}`. To attach/change an unverified email:

```json
{
  "email": "new-email@example.com"
}
```

Returns `200 VerificationChallengeResponse`. Requesting verification for an already verified email returns `409`.

### Confirm email verification

```http
POST /api/v1/auth/email-verifications/confirm
Authorization: Bearer <accessToken>
```

```json
{
  "code": "123456"
}
```

The code must contain exactly six digits. Returns `200 AccountResponse`.

### Request phone verification

```http
POST /api/v1/auth/phone-verifications
Authorization: Bearer <accessToken>
```

```json
{
  "phoneNumber": "+919876543210"
}
```

Returns `200 VerificationChallengeResponse`. This can attach/change an unverified phone number.

### Confirm phone verification

```http
POST /api/v1/auth/phone-verifications/confirm
Authorization: Bearer <accessToken>
```

```json
{
  "phoneNumber": "+919876543210",
  "code": "123456"
}
```

The phone number must match the open challenge. Returns `200 AccountResponse`. Once email and phone are both verified, account status becomes `ACTIVE`.

### Forgot password

```http
POST /api/v1/auth/forgot-password
```

Email variant:

```json
{
  "email": "rajat@example.com"
}
```

Phone variant:

```json
{
  "phoneNumber": "+919876543210"
}
```

Exactly one verified contact is required. Returns `200 VerificationChallengeResponse`.

When the email variant is used, the backend sends the password-reset message to the
verified email address. The email contains the six-digit code, the configured code
expiry, and a link-free instruction to enter the code in the app. The code is never
returned in the HTTP response. If email delivery is unavailable, the request fails
with `503 EMAIL_DELIVERY_FAILED`; clients should allow the user to retry.

The email subject is `Reset your Groovsta password` and the provider template is
`password-reset-email.html`. Template variables are `{{code}}`,
`{{expiresInMinutes}}`, and `{{currentYear}}`. The delivery adapter must HTML-escape
all substituted values and must not log the raw code in production.

### Reset password

```http
POST /api/v1/auth/reset-password
```

```json
{
  "email": "rajat@example.com",
  "code": "123456",
  "newPassword": "NewCorrectHorse456"
}
```

Phone variant:

```json
{
  "phoneNumber": "+919876543210",
  "code": "123456",
  "newPassword": "NewCorrectHorse456"
}
```

Use exactly one of `email` or `phoneNumber`. The code is six digits and `newPassword` is 8–128 characters. Returns `204` and revokes every existing session for the account.

## Profile and social endpoints

### Get current profile

```http
GET /api/v1/me
Authorization: Bearer <accessToken>
```

Returns `200 ProfileResponse` with `viewerState.relationship: SELF` and `canViewContent: true`.

### Update current profile

```http
PATCH /api/v1/me
Authorization: Bearer <accessToken>
```

```json
{
  "displayName": "Rajat S.",
  "bio": "Creator from Delhi",
  "isPublic": false
}
```

`displayName` is required and maximum 80 characters. `bio` is nullable and maximum 500 characters. `isPublic` is optional; omit it to preserve the current visibility. Returns `200 ProfileResponse`.

### Initiate avatar upload

```http
POST /api/v1/me/avatar/uploads
Authorization: Bearer <accessToken>
```

```json
{
  "fileName": "avatar.webp",
  "contentType": "image/webp"
}
```

Allowed content types: `image/jpeg`, `image/png`, `image/webp`. `fileName` is nonblank and maximum 255 characters.

Returns:

```json
{
  "objectKey": "images/avatars/550e8400-e29b-41d4-a716-446655440000/6f5f4df2.webp",
  "uploadUrl": "https://s3.example.com/presigned-put",
  "expiresAt": "2026-07-14T09:45:00Z"
}
```

Upload the bytes directly to S3 before completing:

```http
PUT <uploadUrl>
Content-Type: image/webp

<binary image bytes>
```

The S3 `PUT` does not use the Groovsta bearer token. Use the exact content type supplied during initiation.

### Complete avatar upload

```http
POST /api/v1/me/avatar/uploads/complete
Authorization: Bearer <accessToken>
```

```json
{
  "objectKey": "images/avatars/550e8400-e29b-41d4-a716-446655440000/6f5f4df2.webp"
}
```

The key must be the exact key returned during initiation. The API checks account prefix and S3 existence, attaches the avatar, and removes the old object. Returns `200 ProfileResponse`.

### Get profile by handle

```http
GET /api/v1/profiles/rajat_01
```

Authentication is optional:

```http
GET /api/v1/profiles/creator_01
Authorization: Bearer <accessToken>
```

- With no `Authorization` header, the profile is returned normally and `viewerState` is `null`.
- With a valid bearer token, `viewerState.relationship` is `SELF` for the viewer's own profile, `FOLLOWING` for an active viewer-to-target follow, and `NONE` otherwise.
- `followsViewer` independently reports an active target-to-viewer follow.
- `canViewContent` is `true` for public profiles, the viewer's own profile, and private profiles actively followed by the viewer. It is `false` for other private profiles. Existing content endpoints continue to enforce this visibility decision and return `404` for hidden content.
- A malformed, invalid, expired, or revoked supplied bearer token returns `401`.

Returns `200 ProfileResponse` or `404`. Responses include `Cache-Control: private, no-store` and `Vary: Authorization`, so a personalized representation cannot be reused as a public cached response.

### List followers

```http
GET /api/v1/profiles/creator_01/followers?limit=20&cursor={optionalOpaqueCursor}
```

### List following

```http
GET /api/v1/profiles/creator_01/following?limit=20&cursor={optionalOpaqueCursor}
```

Both endpoints return active accounts newest-follow first and use the same paginated response. Public profile lists are visible to anyone. Private profile lists are visible only to the profile owner and active accepted followers. Anonymous or non-following viewers receive `403`. If an Authorization header is supplied it must be valid or the response is `401`.

```json
{
  "items": [
    {
      "accountId": "6feea895-9b90-477d-a50b-1d9e77f0944d",
      "handle": "diya",
      "displayName": "Diya Sharma",
      "avatarUrl": "https://s3.example.com/temporary-avatar",
      "viewerState": null
    }
  ],
  "nextCursor": "opaque-cursor-or-null"
}
```

`limit` defaults to 20 and is clamped to 1–50. Pass `nextCursor` back unchanged to load the next page. Unknown profile handles return `404`. Responses include `Cache-Control: private, no-store` and `Vary: Authorization`. The `followerCount` and `followingCount` fields in `ProfileResponse` use the same active-account counting rule as these lists, but private-list visibility is enforced only on these list endpoints.

### Search profiles by handle text

```http
GET /api/v1/profiles/search?q=ing&limit=10&cursor={optionalOpaqueCursor}
```

Authentication is optional. With no `Authorization` header, each result has `viewerState: null`. To populate viewer-specific state, supply:

```http
GET /api/v1/profiles/search?q=ing&limit=10&cursor={optionalOpaqueCursor}
Authorization: Bearer <accessToken>
```

When the header is supplied it must contain a valid, unexpired token or the request returns `401`. Only active accounts are returned. `q` is matched case-insensitively anywhere in the handle, so `ing` returns a handle such as `rsingh`, while `che` returns handles such as `check`, `chef_raj`, and `mychef`. A leading `@` is optional. Display names are not matched.

`q` must contain 1–30 letters, numbers, or underscores after trimming and removing an optional leading `@`. `limit` defaults to 10 and is clamped to 1–50. Results are ordered by handle. Pass `nextCursor` back unchanged to load the next page. Results contain the stable account ID and profile fields needed by search and mention autocomplete:

```json
{
  "items": [
    {
      "accountId": "6feea895-9b90-477d-a50b-1d9e77f0944d",
      "handle": "check",
      "displayName": "Check",
      "avatarUrl": "https://s3.example.com/temporary-avatar",
      "viewerState": {
        "relationship": "FOLLOWING",
        "followsViewer": false,
        "canViewContent": true,
        "postsMuted": false,
        "reelsMuted": false,
        "storiesMuted": false
      }
    }
  ],
  "nextCursor": null
}
```

The backend resolves all viewer relationships for a result page in one batch query. Search responses include `Cache-Control: private, no-store` and `Vary: Authorization`.

### Follow profile

```http
POST /api/v1/profiles/creator_01/follow
Authorization: Bearer <accessToken>
```

Returns:

```json
{
  "accountId": "4bc730b2-68d7-42a7-84ad-f61226b4131d",
  "relationship": "FOLLOWING",
  "following": true,
  "followerCount": 11
}
```

Both accounts must be `ACTIVE`. For a public target, following is immediate, returns `FOLLOWING`, and creates a `NEW_FOLLOWER` notification for the profile owner. For a private target, the first call creates a pending request and returns:

```json
{
  "accountId": "4bc730b2-68d7-42a7-84ad-f61226b4131d",
  "relationship": "REQUESTED",
  "following": false,
  "followerCount": 10
}
```

The private profile owner receives a `FOLLOW_REQUEST` notification. Repeating the same request is idempotent. Self-follow returns `409`. `following` is retained temporarily for backward compatibility; clients should migrate to `relationship`.

### Accept or reject a follow request

Use the notification's `referenceId` as `requestId`.

```http
POST /api/v1/follow-requests/{requestId}/accept
Authorization: Bearer <accessToken>
```

Only the private profile owner can accept it. Returns:

```json
{
  "requestId": "8ac66bd4-75c2-43bf-a315-33d22731ef28",
  "requesterAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "FOLLOWING"
}
```

To reject:

```http
DELETE /api/v1/follow-requests/{requestId}
Authorization: Bearer <accessToken>
```

Rejection returns `204` and removes the pending edge. Accepting or rejecting sets the corresponding notification's `actionRequired` to `false`. Acceptance also creates a `FOLLOW_REQUEST_ACCEPTED` notification for the requester; rejection does not create a notification. To follow the requester back, call the existing `POST /api/v1/profiles/{requesterHandle}/follow` endpoint separately.

### Unfollow profile

```http
DELETE /api/v1/profiles/creator_01/follow
Authorization: Bearer <accessToken>
```

Returns the updated `FollowResponse`:

```json
{
  "accountId": "4bc730b2-68d7-42a7-84ad-f61226b4131d",
  "relationship": "NONE",
  "following": false,
  "followerCount": 10
}
```

Unfollowing is idempotent. `followerCount` is the target profile's active follower count after the operation.

### Manage close friends

All close-friend endpoints require bearer authentication and an `ACTIVE` account.

```http
GET /api/v1/me/close-friends
Authorization: Bearer <accessToken>
```

Returns:

```json
{
  "items": [
    {
      "accountId": "6feea895-9b90-477d-a50b-1d9e77f0944d",
      "handle": "diya",
      "displayName": "Diya Sharma",
      "avatarUrl": "https://s3.example.com/temporary-avatar",
      "viewerState": null
    }
  ]
}
```

Add and remove by stable profile handle:

```http
PUT /api/v1/me/close-friends/diya
Authorization: Bearer <accessToken>
```

The add operation is idempotent and returns `ProfileSummaryResponse`. Self-add is rejected. A close friend does not have to follow the owner; membership alone authorizes a `CLOSE_FRIENDS` story.

```http
DELETE /api/v1/me/close-friends/diya
Authorization: Bearer <accessToken>
```

Removal is idempotent and returns `204`.

### Mute and unmute a creator's content

Muting is independent for posts, reels, and stories. It does not unfollow or block the creator and does not prevent direct profile/content navigation.

```http
PUT /api/v1/me/mutes/creator_01/STORY
Authorization: Bearer <accessToken>
```

`contentType` values are `POST`, `REEL`, and `STORY`. The operation is idempotent and returns:

```json
{
  "accountId": "4bc730b2-68d7-42a7-84ad-f61226b4131d",
  "handle": "creator_01",
  "contentType": "STORY",
  "muted": true
}
```

To unmute the same surface:

```http
DELETE /api/v1/me/mutes/creator_01/STORY
Authorization: Bearer <accessToken>
```

This is also idempotent and returns the same shape with `muted: false`. Repeat independently with `POST` and/or `REEL` when the UI offers separate mute switches. Self-muting is rejected.

Mute behavior:

- `STORY` removes that creator from the authenticated `/feeds/stories` tray.
- `POST` removes that creator's posts from authenticated `/feeds/home`.
- `REEL` removes that creator's reels from authenticated `/feeds/home` and `/feeds/reels`.
- Explicit `/profiles/{handle}/posts`, `/profiles/{handle}/reels`, `/profiles/{handle}/stories`, and direct item endpoints remain available subject to normal visibility rules.
- Anonymous feeds have no mute preferences.

## Location endpoints

Location APIs are backend-owned. The mobile app must call Groovsta, not Ola Maps directly. The Ola API key stays on the backend and results are cached before provider calls are made.

Search and reverse lookup require bearer auth to control paid provider usage. Location detail and tagged-content reads are public with optional bearer auth for private-account visibility.

For Ola Maps keys restricted by allowed domain, backend must set `GROOVSTA_OLA_MAPS_ORIGIN` to the public API origin, for example `https://unequal-tamer-enjoying.ngrok-free.dev` in local ngrok testing.

### Search locations

```http
GET /api/v1/locations/search?q=kora&lat=12.9352&lng=77.6245&limit=20
Authorization: Bearer <accessToken>
```

Query parameters:

- `q`: required search text. Default minimum length is 3 characters.
- `lat`, `lng`: optional GPS bias. Use only when the user allowed location permission.
- `limit`: defaults to 20 and is clamped by backend config.

Response:

```json
{
  "items": [
    {
      "id": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
      "provider": "OLA",
      "providerPlaceId": "ola-platform:5000039498427",
      "name": "Koramangala",
      "formattedAddress": "Koramangala, Bengaluru, Karnataka, India",
      "latitude": 12.9352,
      "longitude": 77.6245,
      "city": "Bengaluru",
      "state": "Karnataka",
      "country": "India",
      "postalCode": null
    }
  ],
  "source": "PROVIDER",
  "cacheHit": false
}
```

`source` values are `LOCAL`, `CACHE`, or `PROVIDER`. The backend first checks existing Groovsta locations, then persistent cached provider results, then Ola Maps if enabled and needed.

Only real provider calls count against the backend's per-account location provider rate limit. Local DB hits and cache hits do not count.

### Resolve a selected provider location

Call this when the user selects a search result and the app wants to ensure the location is fully resolved before attaching it to content.

```http
POST /api/v1/locations/resolve
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "provider": "OLA",
  "providerPlaceId": "ola-platform:5000039498427"
}
```

Returns `LocationSummaryResponse`. Use the returned `id` as `locationId` in post, reel, and story create requests.

### Reverse geocode current location

```http
GET /api/v1/locations/reverse?lat=12.9352&lng=77.6245
Authorization: Bearer <accessToken>
```

Returns one `LocationSummaryResponse`. The backend caches reverse-geocode results by rounded coordinate bucket to reduce provider calls.

### Get location details

```http
GET /api/v1/locations/{locationId}
```

Returns `LocationSummaryResponse`.

### Get posts and reels tagged to a location

```http
GET /api/v1/locations/{locationId}/content?type=ALL&limit=20
Authorization: Bearer <optional-accessToken>
```

Query parameters:

- `type`: `ALL`, `POST`, or `REEL`; defaults to `ALL`.
- `cursor`: opaque cursor from the previous response.
- `limit`: defaults to 20 and is clamped to 1–50.

Response:

```json
{
  "location": {
    "id": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
    "provider": "OLA",
    "providerPlaceId": "ola-platform:5000039498427",
    "name": "Koramangala",
    "formattedAddress": "Koramangala, Bengaluru, Karnataka, India",
    "latitude": 12.9352,
    "longitude": 77.6245,
    "city": "Bengaluru",
    "state": "Karnataka",
    "country": "India",
    "postalCode": null
  },
  "items": [
    {
      "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
      "type": "POST",
      "author": {
        "accountId": "550e8400-e29b-41d4-a716-446655440000",
        "handle": "rajat_01",
        "displayName": "Rajat Singh",
        "avatarUrl": null,
        "viewerState": null
      },
      "isFollowingAuthor": false,
      "canShareToStory": true,
      "saved": false,
      "savedAt": null,
      "post": {
        "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
        "status": "PUBLISHED",
        "location": {
          "id": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
          "provider": "OLA",
          "providerPlaceId": "ola-platform:5000039498427",
          "name": "Koramangala",
          "formattedAddress": "Koramangala, Bengaluru, Karnataka, India",
          "latitude": 12.9352,
          "longitude": 77.6245,
          "city": "Bengaluru",
          "state": "Karnataka",
          "country": "India",
          "postalCode": null
        }
      },
      "reel": null,
      "publishedAt": "2026-07-15T10:00:00Z"
    }
  ],
  "nextCursor": null
}
```

Visibility follows the same rules as home/profile feeds: public-profile content is visible to everyone, private-profile content is visible only to the owner and active followers.

### Get active stories tagged to a location

```http
GET /api/v1/locations/{locationId}/stories?limit=20
Authorization: Bearer <optional-accessToken>
```

Returns `StoryPageResponse` using the normal `StoryResponse` shape. Only active, non-expired stories visible to the viewer are returned.

## Content upload endpoints

Posts and reels use one controller and one upload contract. Set `type` to `POST` or `REEL`.

### Initiate an image post

```http
POST /api/v1/content/uploads
Authorization: Bearer <accessToken>
```

```json
{
  "type": "POST",
  "caption": "Photos from tonight",
  "locationId": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
  "hideComments": false,
  "hideLikeCount": false,
  "hideShareCount": false,
  "taggedAccountIds": [
    "3e050d27-7567-45c0-9450-f8b01beb03c8"
  ],
  "media": [
    { "fileName": "stage.jpg", "contentType": "image/jpeg", "sizeBytes": 2457600 },
    { "fileName": "crowd.webp", "contentType": "image/webp", "sizeBytes": 1843200 }
  ]
}
```

A post requires 1–10 JPEG, PNG, or WebP images. The default configured limit is 20 MiB per image. `locationId` is optional; when present it must be a valid Groovsta location returned by `/api/v1/locations/search`, `/api/v1/locations/resolve`, or `/api/v1/locations/reverse`. `hideComments`, `hideLikeCount`, and `hideShareCount` default to `false`; send `true` for the creator toggles enabled during creation. `commentsEnabled` is still accepted for compatibility, but new clients should prefer `hideComments`. `taggedAccountIds` is optional, accepts at most 20 distinct active account IDs, and causes notifications to be sent only after the post is successfully published.

### Initiate a reel

Use the same endpoint with exactly one video:

```json
{
  "type": "REEL",
  "caption": "My first reel",
  "locationId": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
  "hideComments": false,
  "hideLikeCount": false,
  "hideShareCount": false,
  "taggedAccountIds": [
    "3e050d27-7567-45c0-9450-f8b01beb03c8"
  ],
  "media": [
    { "fileName": "dance.mp4", "contentType": "video/mp4", "sizeBytes": 15728640 }
  ]
}
```

A reel accepts `video/mp4`, `video/quicktime`, or `video/webm`. The default configured limit is 500 MiB. `locationId`, `hideComments`, `hideLikeCount`, and `hideShareCount` follow the same rules as posts. `taggedAccountIds` follows the same rules as posts, and notifications are sent only after the reel is published.

Both variants return `ContentUploadResponse`:

```json
{
  "contentId": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
  "type": "POST",
  "uploads": [
    {
      "position": 0,
      "objectKey": "images/posts/b64f5eb5-e05d-494b-bb65-c5896bc0af70/0-d3efb38c.jpg",
      "uploadUrl": "https://s3.example.com/presigned-put",
      "expiresAt": "2026-07-18T10:00:00Z"
    }
  ]
}
```

Upload every item directly to its `uploadUrl` using HTTP `PUT` and the same `Content-Type` supplied during initiation.

### Complete an upload

```http
POST /api/v1/content/{contentId}/uploads/complete
Authorization: Bearer <accessToken>
```

```json
{
  "objectKeys": [
    "images/posts/b64f5eb5-e05d-494b-bb65-c5896bc0af70/0-d3efb38c.jpg"
  ]
}
```

Supply the exact object keys returned during initiation. For a post, the API verifies every image and publishes immediately. For a reel, exactly one key is required; the API verifies the source and changes the reel to `TRANSCODING`.

### Get content

```http
GET /api/v1/content/{contentId}
Authorization: Bearer <optional-owner-token>
```

Returns `ContentResponse`. `type` identifies which nested object is populated:

```json
{
  "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
  "type": "POST",
  "post": { "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70", "status": "PUBLISHED" },
  "reel": null
}
```

Published content is public. An owner token is required to retrieve that owner's unpublished content.

### Publish a reel

```http
POST /api/v1/content/{contentId}/publish
Authorization: Bearer <accessToken>
```

This operation applies only to reels in `READY_TO_PUBLISH`. Image posts are already published during upload completion.

## Saved content endpoints

An authenticated account can save a published post or reel owned by another account:

```http
POST /api/v1/content/{contentId}/saves
Authorization: Bearer <accessToken>
```

The target must currently be visible to the viewer. A public creator is visible to any active authenticated account; a private creator requires an active viewer-to-creator follow. Unpublished, deleted, taken-down, inactive-owner, unavailable, and hidden private content return `404`. Saving the viewer's own content returns `409`.

Saving is idempotent and returns:

```json
{
  "contentId": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
  "saved": true,
  "savedAt": "2026-07-26T12:30:00Z"
}
```

Calling the endpoint again preserves the original `savedAt`.

Remove a save with:

```http
DELETE /api/v1/content/{contentId}/saves
Authorization: Bearer <accessToken>
```

This operation is also idempotent:

```json
{
  "contentId": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
  "saved": false,
  "savedAt": null
}
```

Unsave does not require the content to remain visible. The client can therefore remove a local save by ID even if the creator has become private or the content was taken down.

Saved Posts and Reels are fetched separately for two frontend tabs:

```http
GET /api/v1/me/saved/posts?limit=20&cursor={optionalOpaqueCursor}
GET /api/v1/me/saved/reels?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <accessToken>
```

Both return `SavedContentPageResponse`, ordered by `savedAt` descending:

```json
{
  "items": [
    {
      "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
      "type": "POST",
      "author": {
        "accountId": "550e8400-e29b-41d4-a716-446655440000",
        "handle": "creator_01",
        "displayName": "Creator",
        "avatarUrl": "https://s3.example.com/temporary-avatar",
        "viewerState": null
      },
      "isFollowingAuthor": false,
      "canShareToStory": true,
      "saved": true,
      "savedAt": "2026-07-26T12:30:00Z",
      "post": {
        "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
        "status": "PUBLISHED",
        "saved": true
      },
      "reel": null,
      "publishedAt": "2026-07-20T10:00:00Z"
    }
  ],
  "nextCursor": "opaque-cursor-or-null"
}
```

The Reels endpoint uses the same item shape with `type: "REEL"`, `post: null`, and a populated `reel`.

`limit` defaults to 20 and is clamped to 1–50. Pass `nextCursor` back unchanged; it encodes save time and the internal ordering tie-breaker. Posts and Reels require independent cursors and pagination state.

Visibility is evaluated on every list request without deleting the saved row:

- If a saved public creator becomes private and the viewer does not follow them, their items disappear from both saved-list responses.
- If that creator becomes public again, the same saved items automatically reappear after refresh.
- If the viewer starts actively following the private creator, the items automatically reappear.
- Unfollowing a private creator hides their saved items again.
- Suspended creators and content that is no longer `PUBLISHED` are hidden.

This behavior is query-based; privacy changes do not rewrite or delete `content_saved_items`. Save records are deleted only when the viewer explicitly unsaves, the content is physically deleted, or the account is deleted.

Authenticated Home items also expose top-level `saved` and nullable `savedAt`. The nested `PostResponse` or `ReelResponse` exposes the same `saved` boolean. Anonymous responses use `saved: null` and `savedAt: null`. All saved-content responses use `Cache-Control: private, no-store` and `Vary: Authorization`.

`canShareToStory` is a viewer-specific boolean on `PostResponse`, `ReelResponse`, and top-level feed/saved-feed items. It is `true` only when the authenticated viewer can create a story from that post or reel. Current rule: the viewer owns the content, or the content owner's profile is public. Anonymous responses return `false`.

## Notification endpoints

Notifications are persisted in-app, delivered live over authenticated STOMP, and queued for FCM delivery to every active installation. A notification is created only when the underlying action is newly created, and self-actions do not create notifications. The persisted inbox remains the source of truth when a socket or push is delayed or missed.

```http
GET /api/v1/notifications?limit=20&cursor={optionalOpaqueCursor}&unreadOnly=false
Authorization: Bearer <accessToken>
```

Returns:

```json
{
  "items": [
    {
      "id": "a4fcb319-0b83-4a0c-8dde-c30985771ab7",
      "type": "FOLLOW_REQUEST",
      "actor": {
        "accountId": "550e8400-e29b-41d4-a716-446655440000",
        "handle": "diya",
        "displayName": "Diya Sharma",
        "avatarUrl": "https://s3.example.com/temporary-avatar",
        "viewerState": null
      },
      "targetType": "PROFILE",
      "targetId": "550e8400-e29b-41d4-a716-446655440000",
      "referenceId": "8ac66bd4-75c2-43bf-a315-33d22731ef28",
      "contextId": null,
      "preview": null,
      "read": false,
      "actionRequired": true,
      "createdAt": "2026-07-28T15:30:00Z"
    }
  ],
  "nextCursor": null
}
```

Notification mappings:

| Type | `targetId` | `referenceId` | Frontend action |
|---|---|---|---|
| `FOLLOW_REQUEST` | Requester's account ID | Follow request ID | Open actor profile or accept/reject |
| `NEW_FOLLOWER` | New follower's account ID | Follow edge ID | Open the follower's profile |
| `FOLLOW_REQUEST_ACCEPTED` | Acceptor's account ID | Follow request ID | Open the acceptor's profile |
| `CONTENT_LIKED` | Post/reel ID | Like ID | Open post or reel |
| `CONTENT_TAGGED` | Post/reel ID | Tag ID | Open post or reel |
| `STORY_LIKED` | Story ID | Like ID | Open story |
| `CONTENT_COMMENTED` | Post/reel ID | Comment ID | Open the comment thread |
| `STORY_COMMENTED` | Story ID | Comment ID | Open the story comments |
| `COMMENT_LIKED` | Parent post/reel or story ID | Comment-like ID | Open `contextId`, the liked comment ID |
| `COMMENT_MENTIONED` | Parent post/reel or story ID | Mention ID | Open `contextId`, the mentioning comment ID |
| `DIRECT_MESSAGE` | Conversation ID | Message ID | Open the conversation |
| `MISSED_CALL` | Call ID | Call-participant ID | Open call history/details |

`contextId` is nullable. For `COMMENT_LIKED` and `COMMENT_MENTIONED`, it contains the exact comment ID while `targetType` and `targetId` identify whether the parent is content or a story. If a comment's normal recipient is also mentioned, the backend emits only `COMMENT_MENTIONED` for that recipient to avoid duplicate alerts.

Use `GET /api/v1/notifications/unread-count` for the tab badge. It returns `{ "unreadCount": 3 }`.

Mark one item read:

```http
POST /api/v1/notifications/{notificationId}/read
Authorization: Bearer <accessToken>
```

Mark the complete inbox read:

```http
POST /api/v1/notifications/read-all
Authorization: Bearer <accessToken>
```

The inbox is cursor-paginated newest-first. Pass `nextCursor` back unchanged. `limit` is clamped to 1–50.

### Register an installation for FCM

After signup/login and after every FCM token refresh, register the installation using the same stable `X-Device-Id` and concrete platform used for authentication:

```http
PUT /api/v1/notifications/push-token
Authorization: Bearer <accessToken>
X-Device-Id: android-550e8400
Content-Type: application/json

{
  "registrationId": "<fcm-registration-token>",
  "identifierType": "FCM_TOKEN"
}
```

`identifierType` is `FCM_TOKEN` (the normal Android, iOS, and web integration) or `FIREBASE_INSTALLATION_ID`. The authenticated device must have been registered with `X-Platform: ANDROID`, `IOS`, or `WEB`; devices created as `UNKNOWN` are rejected. A token moved to another authenticated installation is disabled on its previous installation.

The response never echoes the secret registration value:

```json
{
  "id": "5d2752aa-0f84-41f6-a586-97a9af07fc07",
  "deviceIdentifier": "android-550e8400",
  "platform": "ANDROID",
  "identifierType": "FCM_TOKEN",
  "active": true,
  "registeredAt": "2026-08-01T08:30:00Z"
}
```

Before deleting the local Firebase token, opt this installation out:

```http
DELETE /api/v1/notifications/push-token
Authorization: Bearer <accessToken>
X-Device-Id: android-550e8400
```

Logout automatically disables the token associated with that login session's device. A successful password reset revokes all sessions and disables every installation for the account. Register the current token again after the next login. Firebase `UNREGISTERED` responses also disable invalid registrations automatically.

### Live in-app notification events

Use the authenticated STOMP connection at `/ws/chat` and subscribe to:

```text
/user/queue/notification-events
```

Events have this shape:

```json
{
  "eventId": "f40196ab-c158-4d61-876d-b866c2f7c32f",
  "type": "CREATED",
  "notification": {
    "id": "a4fcb319-0b83-4a0c-8dde-c30985771ab7",
    "type": "CONTENT_LIKED",
    "targetType": "CONTENT",
    "targetId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
    "referenceId": "18a40d29-ac04-4907-a9ea-6440f77eff52",
    "contextId": null,
    "read": false,
    "actionRequired": false,
    "createdAt": "2026-08-01T08:31:00Z"
  },
  "occurredAt": "2026-08-01T08:31:00Z"
}
```

`type` is `CREATED`, `UPDATED`, `READ`, or `ALL_READ`. `notification` is `null` only for `ALL_READ`. Deduplicate by `eventId`, update the local list optimistically, and reconcile from the inbox/unread-count endpoints after reconnect. `UPDATED` is currently sent when an actionable follow request is resolved.

### FCM payload contract

Every persisted notification type in the mapping table is eligible for FCM. Notification pushes include a visible title/body plus string-valued data:

```json
{
  "eventType": "IN_APP_NOTIFICATION",
  "messageType": "NOTIFICATION",
  "pushDeliveryId": "7a29f985-52ae-4b26-9f13-9cb9a954cbb1",
  "notificationId": "a4fcb319-0b83-4a0c-8dde-c30985771ab7",
  "notificationType": "CONTENT_LIKED",
  "targetType": "CONTENT",
  "targetId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "referenceId": "18a40d29-ac04-4907-a9ea-6440f77eff52",
  "unreadCount": "3"
}
```

`contextId` is included when present. Deduplicate push handling by `pushDeliveryId`; then use the same navigation mapping as an inbox item. Message/comment text is hidden from lock-screen pushes by default. The backend stores one delivery per installation, retries temporary Firebase errors with bounded exponential backoff, and does not call Firebase inside the originating business transaction.

Backend FCM configuration:

```text
GROOVSTA_PUSH_ENABLED=true
GROOVSTA_FIREBASE_PROJECT_ID=<firebase-project-id>
GROOVSTA_FIREBASE_CREDENTIALS_PATH=/run/secrets/firebase-service-account.json
```

Instead of `GROOVSTA_FIREBASE_CREDENTIALS_PATH`, supply `GROOVSTA_FIREBASE_CREDENTIALS_BASE64` or leave both credential fields empty to use Google Application Default Credentials. Never commit a service-account JSON file or ship it to the frontend.

## Chat endpoints

Chat is authenticated, one-to-one, and available only to `ACTIVE` accounts. It provides live in-app delivery, delivery/seen receipts, and presence through WebSocket. Every newly persisted DM also creates an in-app notification and an FCM delivery for the recipient.

### Conversation inbox

```http
GET /api/v1/chat/conversations?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <accessToken>
```

Returns users the current account has exchanged at least one message with, ordered by latest message:

```json
{
  "items": [
    {
      "id": "0e62ad67-07b6-488d-873d-1fe528660af7",
      "otherUser": {
        "accountId": "550e8400-e29b-41d4-a716-446655440000",
        "handle": "creator_01",
        "displayName": "Creator",
        "avatarUrl": "https://s3.example.com/temporary-avatar",
        "viewerState": null
      },
      "otherUserOnline": true,
      "lastMessage": {
        "id": "55b21e90-5b2f-4663-b564-788103371d06",
        "conversationId": "0e62ad67-07b6-488d-873d-1fe528660af7",
        "sender": {
          "accountId": "550e8400-e29b-41d4-a716-446655440000",
          "handle": "creator_01",
          "displayName": "Creator",
          "avatarUrl": null,
          "viewerState": null
        },
        "type": "TEXT",
        "text": "Hey, did you see this?",
        "sharedContent": null,
        "attachments": [],
        "replyTo": null,
        "reactions": [{ "emoji": "🔥", "count": 2, "reactedByViewer": true }],
        "deliveryStatus": "DELIVERED",
        "isRead": false,
        "sentAt": "2026-07-26T12:30:00Z",
        "editedAt": null,
        "unsent": false,
        "unsentAt": null
      },
      "hasUnread": true,
      "updatedAt": "2026-07-26T12:30:00Z"
    }
  ],
  "nextCursor": "opaque-cursor-or-null"
}
```

`deliveryStatus` is `SENT`, `DELIVERED`, or `SEEN`. `isRead` remains as a compatibility alias for `deliveryStatus == SEEN`. `hasUnread` is true only when the last message was received by the current account and has not been seen. `otherUserOnline` is the current in-memory WebSocket presence snapshot.

### Message history

```http
GET /api/v1/chat/conversations/{conversationId}/messages?limit=30&cursor={optionalOpaqueCursor}
Authorization: Bearer <accessToken>
```

Only a conversation participant can read its history. Messages are returned newest first. Pass `nextCursor` back unchanged to load older messages. `limit` defaults to 30 and is clamped to 1–50.

Changing profile privacy or follow state never deletes existing history. It can prevent new messages while leaving both participants able to read the existing conversation.

### Send a text message

```http
POST /api/v1/chat/messages
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "clientMessageId": "3e9cbdb7-52d2-4718-8105-19cf0ba399f6",
  "recipientAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "TEXT",
  "text": "Hey, did you see this?",
  "sharedContentId": null,
  "replyToMessageId": null
}
```

`clientMessageId` must be generated once by the frontend and reused if the same send is retried. Repeating the same request is idempotent. Reusing it for different content returns `409`.

Text is required for `TEXT`, trimmed by the API, and limited to 2,000 characters.

### Reply to a message

Set `replyToMessageId` on a `TEXT`, `CONTENT_SHARE`, or media-upload initiation request. The referenced message must exist, belong to the same direct conversation, and not be unsent. Responses contain a non-recursive `replyTo` preview with the original sender, text, shared-content card or media attachments, and unsent state.

### Share a post or reel

```json
{
  "clientMessageId": "9cf94aef-2fa0-40f4-9854-d686f7e5bf39",
  "recipientAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "CONTENT_SHARE",
  "text": "Watch this",
  "sharedContentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "replyToMessageId": null
}
```

The message returns a native preview rather than a pasted link:

```json
{
  "id": "9222dbb0-84ee-4fc9-b067-f67665704a78",
  "conversationId": "0e62ad67-07b6-488d-873d-1fe528660af7",
  "sender": {
    "accountId": "e23f2ad6-eb20-4d8b-860a-b9aa10f53c4d",
    "handle": "rajat_01",
    "displayName": "Rajat",
    "avatarUrl": null,
    "viewerState": null
  },
  "type": "CONTENT_SHARE",
  "text": "Watch this",
  "sharedContent": {
    "contentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
    "available": true,
    "type": "REEL",
    "author": {
      "accountId": "a61047dc-9c6f-41be-b197-29b860931dae",
      "handle": "dance_creator",
      "displayName": "Dance Creator",
      "avatarUrl": null,
      "viewerState": null
    },
    "caption": "Practice session",
    "previewUrl": "https://s3.example.com/temporary-thumbnail",
    "liked": true,
    "mediaCount": null,
    "durationSeconds": 28
  },
  "attachments": [],
  "replyTo": null,
  "reactions": [],
  "deliveryStatus": "SENT",
  "isRead": false,
  "sentAt": "2026-07-26T12:35:00Z",
  "editedAt": null,
  "unsent": false,
  "unsentAt": null
}
```

Only published posts and reels currently visible to both sender and recipient can be shared. Post previews use the first carousel image and `mediaCount`; reel previews use the thumbnail and `durationSeconds`. If the content is later hidden, taken down, deleted, or owned by an inactive account, history returns the reference with `available: false` and no preview details.

Shared post and reel messages support the same reply, reaction, delivery, seen, edit-text, and unsend behavior as text messages.

### Send photos and videos

Chat media is owned by messaging records and uses private S3 objects. It does not create a post, reel, story, or pasted URL. Generate `clientMessageId` once and initiate one batch containing every attachment in the message:

```http
POST /api/v1/chat/attachments/uploads
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "clientMessageId": "bb6313e4-11b6-42a0-b9fa-640cb3a532ca",
  "recipientAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "text": "From yesterday",
  "replyToMessageId": null,
  "attachments": [
    {
      "mediaType": "IMAGE",
      "fileName": "photo.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 2483910,
      "width": 3024,
      "height": 4032,
      "durationMillis": null
    },
    {
      "mediaType": "VIDEO",
      "fileName": "clip.mp4",
      "contentType": "video/mp4",
      "sizeBytes": 18392014,
      "width": 1080,
      "height": 1920,
      "durationMillis": 12400
    }
  ]
}
```

The response contains one presigned PUT per attachment:

```json
{
  "uploadBatchId": "849f1df7-613d-4dd2-a4e9-35971205617f",
  "clientMessageId": "bb6313e4-11b6-42a0-b9fa-640cb3a532ca",
  "status": "PENDING",
  "messageId": null,
  "uploads": [
    {
      "attachmentId": "9683798e-c85b-4597-9368-6aebc9bd4ca7",
      "position": 0,
      "mediaType": "IMAGE",
      "uploadUrl": "https://s3.example.com/temporary-presigned-put",
      "requiredHeaders": { "Content-Type": "image/jpeg" },
      "expiresAt": "2026-08-02T13:30:00Z"
    }
  ],
  "expiresAt": "2026-08-02T13:30:00Z"
}
```

Upload each binary directly to its URL using every `requiredHeaders` value exactly. Do not send the Groovsta bearer token to S3:

```http
PUT <uploadUrl>
Content-Type: image/jpeg

<binary bytes>
```

After all PUTs succeed, complete the batch without supplying S3 keys or URLs:

```http
POST /api/v1/chat/attachments/uploads/{uploadBatchId}/complete
Authorization: Bearer <accessToken>
```

Completion verifies every private object with S3 HEAD, including exact content type and byte size, rechecks messaging permission and the reply target, then atomically creates one `MEDIA` message. Missing, partial, incorrectly typed, or incorrectly sized uploads return `409` and create no message. A successful response is a `ChatMessageResponse`:

```json
{
  "id": "3049f0de-45a8-4b49-b4ce-b11f28a48cb4",
  "conversationId": "0e62ad67-07b6-488d-873d-1fe528660af7",
  "sender": {
    "accountId": "e23f2ad6-eb20-4d8b-860a-b9aa10f53c4d",
    "handle": "rajat_01",
    "displayName": "Rajat",
    "avatarUrl": null,
    "viewerState": null
  },
  "type": "MEDIA",
  "text": "From yesterday",
  "sharedContent": null,
  "attachments": [
    {
      "id": "9683798e-c85b-4597-9368-6aebc9bd4ca7",
      "mediaType": "IMAGE",
      "status": "READY",
      "mediaUrl": "https://s3.example.com/temporary-presigned-get",
      "thumbnailUrl": "https://s3.example.com/temporary-presigned-get",
      "width": 3024,
      "height": 4032,
      "durationMillis": null,
      "contentType": "image/jpeg",
      "sizeBytes": 2483910
    },
    {
      "id": "08a89623-65ee-40fc-8674-c69fb58613e6",
      "mediaType": "VIDEO",
      "status": "PROCESSING",
      "mediaUrl": "https://s3.example.com/temporary-source-preview",
      "thumbnailUrl": null,
      "width": 1080,
      "height": 1920,
      "durationMillis": 12400,
      "contentType": "video/mp4",
      "sizeBytes": 18392014
    }
  ],
  "replyTo": null,
  "reactions": [],
  "deliveryStatus": "SENT",
  "isRead": false,
  "sentAt": "2026-08-02T13:15:10Z",
  "editedAt": null,
  "unsent": false,
  "unsentAt": null
}
```

Attachment statuses are `PROCESSING`, `READY`, and `FAILED` after message creation. Images become `READY` at completion. Videos enter `PROCESSING`; the shared FFmpeg worker later emits `MESSAGE_UPDATED` with the same attachment ID and either a transcoded `READY` media/thumbnail pair or `FAILED`. Render a processing or retry-unavailable placeholder from `status` instead of interpreting URL presence.

`attachmentId`, `messageId`, and `uploadBatchId` are stable identifiers. Every `uploadUrl`, `mediaUrl`, and `thumbnailUrl` is temporary and must never be persisted as an identifier. Refetch message history when a download URL expires.

Current limits:

- 1–10 attachments per message.
- JPEG, PNG, WebP, HEIC, and HEIF images; maximum 20 MiB each.
- MP4, QuickTime, and WebM videos; maximum 500 MiB each and 15 minutes when duration is supplied.
- Maximum 500 MiB combined per message.
- Width and height are required and each is limited to 12,000 pixels.
- Optional caption text is limited to 2,000 characters.

Initiation is idempotent by sender plus `clientMessageId`. Retrying the identical initiation returns the same batch/attachment IDs with fresh presigned URLs; changing any recipient, caption, reply, order, or attachment metadata returns `409`. Completion is also idempotent and returns the existing message without duplicating the WebSocket event or notification. If a URL expires, repeat initiation with the same request and client ID, upload again, and complete the same batch.

Media messages support the same optional reply, emoji reaction, delivery, seen, 10-second caption edit, and unlimited-time unsend operations as other messages. Unsend retains attachment rows and private objects for audit/moderation but removes attachments and their temporary URLs from API and WebSocket responses. Notification previews use safe summaries such as `Sent a photo`, `Sent a video`, or a plural equivalent.

### Edit and unsend

```http
PATCH /api/v1/chat/messages/{messageId}
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "text": "Corrected text" }
```

Only the sender can edit, and only through 10 seconds after `sentAt`. A `TEXT` message cannot be edited to blank; content-share and media messages may have their optional accompanying text removed. Successful responses populate `editedAt`. An expired edit window returns `409`.

```http
DELETE /api/v1/chat/messages/{messageId}
Authorization: Bearer <accessToken>
```

Only the sender can unsend. There is no time limit and the operation is idempotent. The database retains the original message for audit/moderation, while API responses expose `unsent: true` and hide text, content preview, attachments, reply preview, and reactions.

### Emoji reactions

```http
PUT /api/v1/chat/messages/{messageId}/reaction
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "emoji": "🔥" }
```

Each participant has one reaction per non-unsent message. Calling `PUT` again replaces that account's emoji. Any nonblank Unicode value up to 32 characters is accepted so the frontend should use an emoji picker. This works identically for text, post-share, reel-share, and media messages.

```http
DELETE /api/v1/chat/messages/{messageId}/reaction
Authorization: Bearer <accessToken>
```

Removal is idempotent. `reactions` is grouped by emoji and includes `count` and viewer-specific `reactedByViewer`.

### Messaging permission

Permission is checked on every send:

- Public recipient: any `ACTIVE` account may send.
- Private recipient: the sender must actively follow the recipient.
- Existing conversation history does not bypass the current privacy/follow rule.
- Sender and recipient must both be `ACTIVE`.
- Self-messaging returns `409`.
- A disallowed private-account send returns `403`.

Account blocking, group chat, and typing indicators are not part of this implementation.

### Delivered and seen

After receiving messages through WebSocket or REST catch-up, the recipient acknowledges the newest received message:

```http
POST /api/v1/chat/conversations/{conversationId}/delivered
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "messageId": "55b21e90-5b2f-4663-b564-788103371d06" }
```

This marks all incoming messages through that message as delivered. A sender cannot mark their own message delivered.

```http
POST /api/v1/chat/conversations/{conversationId}/read
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "messageId": "55b21e90-5b2f-4663-b564-788103371d06"
}
```

This marks the conversation seen through that message and implicitly marks incoming messages delivered. Sending an older message ID never moves seen state backwards.

### Online presence

```http
GET /api/v1/chat/presence/{accountId}
Authorization: Bearer <accessToken>
```

Returns `{ "accountId": "...", "online": true, "changedAt": "..." }`. An account is online while at least one authenticated chat WebSocket session is connected. Multiple devices are supported. Presence is currently in-memory and instance-local; production multi-instance deployment requires shared presence storage.

### Live WebSocket contract

Connect a STOMP client to:

```text
ws://localhost:8080/ws/chat
```

Supply the opaque session token in the STOMP `CONNECT` headers:

```text
Authorization: Bearer <accessToken>
```

Subscribe after connection:

```text
/user/queue/chat-events
/user/queue/chat-errors
```

Send the same `SendChatMessageRequest` JSON used by REST to:

```text
/app/chat.send
```

Send read state to:

```text
/app/chat.read
```

Additional application destinations:

```text
/app/chat.edit
/app/chat.unsend
/app/chat.react
/app/chat.reaction.remove
/app/chat.delivered
```

```json
{
  "conversationId": "0e62ad67-07b6-488d-873d-1fe528660af7",
  "messageId": "55b21e90-5b2f-4663-b564-788103371d06"
}
```

`/user/queue/chat-events` emits `MESSAGE_CREATED`, `MESSAGE_UPDATED`, `MESSAGE_UNSENT`, `MESSAGE_REACTION_UPDATED`, `MESSAGE_DELIVERED`, `MESSAGE_READ`, and `PRESENCE_CHANGED`. Message events contain the updated viewer-specific `message`; receipt events contain `deliveryReceipt` or `readReceipt`; presence events contain `presence`. Media completion emits `MESSAGE_CREATED` with attachments, and every video processing completion/failure emits `MESSAGE_UPDATED` containing the full attachment list. Database-backed events are emitted after transaction commit. Use REST inbox/history and the presence endpoint as the source of truth after reconnecting.

## Audio and video call endpoints

Calls are authenticated and available only to `ACTIVE` accounts. The backend persists lifecycle/history, enforces permissions, relays WebRTC signaling, and emits live state changes. Audio/video bytes travel directly between frontend WebRTC peers and never pass through this API.

Direct and group calls share the same API. The beta topology is a WebRTC mesh capped at eight total participants: the host plus at most seven invitees. Each joined frontend maintains one `RTCPeerConnection` per other joined participant.

### Start a call

Generate `clientCallId` once and reuse it for retries of the same operation:

```http
POST /api/v1/calls
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "clientCallId": "61f40886-3bbd-4db5-a329-b2a5d25c7318",
  "type": "VIDEO",
  "participantAccountIds": [
    "550e8400-e29b-41d4-a716-446655440000",
    "0ff3f010-9434-44d0-a2d4-0c70ef314cbc"
  ]
}
```

`type` is `AUDIO` or `VIDEO`. The participant set must contain 1–7 distinct invitees and must not contain the caller. Repeating the same initial request is idempotent, including after more people were invited later. Reusing a client ID for a different type or initial invitee set returns `409`.

The response shape is:

```json
{
  "id": "c9cf8251-080e-460d-8e51-ac89c2de2391",
  "clientCallId": "61f40886-3bbd-4db5-a329-b2a5d25c7318",
  "type": "VIDEO",
  "status": "RINGING",
  "endReason": null,
  "initiator": {
    "accountId": "8f4ff287-3340-4603-985d-a96c1a78b52a",
    "handle": "rajat_01",
    "displayName": "Rajat",
    "avatarUrl": null,
    "viewerState": null
  },
  "participants": [
    {
      "id": "48c8d833-5460-44cd-ab0a-74095f5cbf5d",
      "account": {
        "accountId": "8f4ff287-3340-4603-985d-a96c1a78b52a",
        "handle": "rajat_01",
        "displayName": "Rajat",
        "avatarUrl": null,
        "viewerState": null
      },
      "invitedBy": {
        "accountId": "8f4ff287-3340-4603-985d-a96c1a78b52a",
        "handle": "rajat_01",
        "displayName": "Rajat",
        "avatarUrl": null,
        "viewerState": null
      },
      "role": "HOST",
      "status": "JOINED",
      "microphoneMuted": false,
      "cameraEnabled": true,
      "invitedAt": "2026-08-01T10:00:00Z",
      "joinedAt": "2026-08-01T10:00:00Z",
      "leftAt": null
    }
  ],
  "groupCall": true,
  "maxParticipants": 8,
  "ringExpiresAt": "2026-08-01T10:00:30Z",
  "startedAt": null,
  "endedAt": null,
  "createdAt": "2026-08-01T10:00:00Z",
  "viewerState": {
    "participantStatus": "JOINED",
    "canAccept": false,
    "canDecline": false,
    "canCancel": true,
    "canEnd": false,
    "canLeave": false,
    "canInvite": true
  }
}
```

Call statuses are `RINGING`, `ACTIVE`, and `ENDED`. Participant statuses are `INVITED`, `JOINED`, `DECLINED`, `LEFT`, `MISSED`, and `CANCELLED`. Final reasons are `COMPLETED`, `DECLINED`, `CANCELLED`, `MISSED`, and `FAILED`. Render actions from `viewerState` rather than duplicating lifecycle logic in the client.

### Privacy, busy state, and invitation rules

- All participants must be active and have profiles.
- A public account can be called by any active account. A private account requires the caller to follow it.
- In a group, every participant must be permitted to contact every private participant. This prevents a group invitation from exposing a private account to an otherwise disallowed peer.
- An `INVITED` or `JOINED` account in a `RINGING` or `ACTIVE` call is busy. If any selected account is busy, creation/invitation returns `409` atomically.
- A pending invitation expires after 30 seconds by default. Expired acceptance returns `410`.
- Starting a call is all-or-nothing: the API never creates a partial group if one participant is invalid, private, busy, duplicated, or over the limit.

### Incoming call and lifecycle actions

Foreground clients receive `INCOMING_CALL` through `/user/queue/call-events`. They then call one of:

```http
POST /api/v1/calls/{callId}/accept
POST /api/v1/calls/{callId}/decline
Authorization: Bearer <accessToken>
```

The first invited participant to accept moves the call from `RINGING` to `ACTIVE`. A group invitee can decline independently; others continue. If everyone declines or times out before anyone accepts, the call ends.

The initiator can cancel before activation with `POST /api/v1/calls/{callId}/cancel`. Any joined participant can leave an active call with `POST /api/v1/calls/{callId}/leave`.

If the host leaves while at least two other participants remain, the earliest joined remaining participant becomes `HOST`. When fewer than two participants remain, the call ends. The current host can end it for everyone with `POST /api/v1/calls/{callId}/end`.

The current host can add invitees while ringing or active:

```http
POST /api/v1/calls/{callId}/participants
Authorization: Bearer <accessToken>
Content-Type: application/json

{ "participantAccountIds": ["6ac7ec33-d0c9-4e1f-8eba-5287a4838802"] }
```

Former, declined, or missed members cannot be re-added to the same call, and the lifetime participant count cannot exceed eight.

### Catch-up and call history

On app launch or WebSocket reconnect, fetch ongoing calls before relying on live events:

```http
GET /api/v1/calls/active
GET /api/v1/calls/{callId}
Authorization: Bearer <accessToken>
```

Call history is newest-first and cursor-paginated:

```http
GET /api/v1/calls?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <accessToken>
```

Only participants can read a call. Pass `nextCursor` back unchanged.

### ICE configuration

Fetch ICE servers before constructing peer connections and refresh them when the app session reconnects:

```http
GET /api/v1/calls/ice-configuration
Authorization: Bearer <accessToken>
```

```json
{
  "iceServers": [
    { "urls": ["stun:stun.l.google.com:19302"], "username": null, "credential": null },
    { "urls": ["turn:turn.example.com:3478"], "username": "temporary-user", "credential": "temporary-secret" }
  ],
  "groupParticipantLimit": 8,
  "heartbeatIntervalSeconds": 30
}
```

STUN alone is not enough for all mobile/carrier/NAT combinations. Configure `GROOVSTA_CALL_TURN_URLS`, `GROOVSTA_CALL_TURN_USERNAME`, and `GROOVSTA_CALL_TURN_CREDENTIAL` together for reliable calls.

### Call WebSocket contract

Calls use the existing authenticated STOMP connection at `/ws/chat`. Subscribe to:

```text
/user/queue/call-events
/user/queue/call-errors
```

Call events are `CALL_CREATED`, `INCOMING_CALL`, `PARTICIPANT_INVITED`, `PARTICIPANT_JOINED`, `PARTICIPANT_DECLINED`, `PARTICIPANT_LEFT`, `PARTICIPANT_MISSED`, `PARTICIPANT_MEDIA_CHANGED`, `CALL_ENDED`, and `SIGNAL`. Lifecycle events contain a complete viewer-specific `call`; `SIGNAL` contains only `signal`. Deduplicate reconnect/retry deliveries by `eventId`.

Send offers, answers, ICE candidates, and ICE restarts to `/app/call.signal`:

```json
{
  "callId": "c9cf8251-080e-460d-8e51-ac89c2de2391",
  "recipientAccountId": "550e8400-e29b-41d4-a716-446655440000",
  "type": "OFFER",
  "sdp": "v=0...",
  "candidate": null,
  "sdpMid": null,
  "sdpMLineIndex": null
}
```

`OFFER`, `ANSWER`, and `ICE_RESTART` require `sdp`. `ICE_CANDIDATE` requires `candidate` and may include `sdpMid` and a non-negative `sdpMLineIndex`. Sender and recipient must both be joined members of the same active call; clients can never choose an arbitrary relay recipient.

For each joined pair, use lexicographic account UUID order to avoid offer glare: the client with the lower account UUID creates the initial offer. Use WebRTC perfect-negotiation handling for later renegotiation and ICE restarts.

Send media state changes to `/app/call.media-state`:

```json
{
  "callId": "c9cf8251-080e-460d-8e51-ac89c2de2391",
  "microphoneMuted": true,
  "cameraEnabled": false
}
```

Camera cannot be enabled for an `AUDIO` call. REST `PATCH /api/v1/calls/{callId}/media-state` is the fallback.

While joined, send `/app/call.heartbeat` at the interval returned by ICE configuration:

```json
{ "callId": "c9cf8251-080e-460d-8e51-ac89c2de2391" }
```

REST `POST /api/v1/calls/heartbeat` is the fallback. Missing heartbeats eventually mark a participant left; if fewer than two joined participants remain, the call ends with `FAILED`.

Call lifecycle events are also queued through FCM for background/terminated clients. An incoming invite is a visible, high-priority `CALL` push with a short TTL and `callId`, `callType`, `callEventType: INCOMING_CALL`, and `eventId`. Later lifecycle changes are data-only `CALL` pushes with a 60-second TTL and the same collapse key, allowing the client to cancel stale ringing. Deduplicate socket/push overlap by `eventId`, fetch `GET /api/v1/calls/{callId}` or `/active` before acting, and never trust push data as the final call state. If an invite expires, the backend also creates the persisted `MISSED_CALL` inbox notification.

## Feed endpoints

### Public reels feed

```http
GET /api/v1/feeds/reels?limit=20&before=2026-07-14T10:00:00Z
Authorization: Bearer <optional-accessToken>
```

- `before` is optional ISO-8601 UTC time; omitted means now.
- `limit` defaults to 20 and is clamped to 1–50.
- Only `PUBLISHED` reels owned by public profiles are returned, newest first.
- When a valid bearer token is supplied, creators muted for `REEL` are excluded. Invalid supplied credentials return `401`.
- Responses use `Cache-Control: private, no-store` and `Vary: Authorization`.

The dedicated reels feed returns `FeedResponse`:

```json
{
  "items": [
    {
      "id": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
      "ownerAccountId": "550e8400-e29b-41d4-a716-446655440000",
      "caption": "My first reel",
      "status": "PUBLISHED",
      "playbackUrl": "https://s3.example.com/presigned-video",
      "thumbnailUrl": "https://s3.example.com/presigned-thumbnail",
      "durationSeconds": 28,
      "likeCount": 10,
      "liked": false,
      "commentCount": 3,
      "publishedAt": "2026-07-18T10:00:00Z",
      "createdAt": "2026-07-18T09:40:00Z",
      "moderationReason": null
    }
  ],
  "nextCursor": "2026-07-18T10:00:00Z"
}
```

### Unified home feed

```http
GET /api/v1/feeds/home?limit=20
Authorization: Bearer <optional-accessToken>
```

Authentication is optional:

- Without a bearer token, Home returns posts and reels from public profiles, newest first.
- With a bearer token, Home returns the current account's and followed accounts' content first. After that tier is exhausted, it returns content from all other public profiles.
- With a bearer token, `POST` and `REEL` mute preferences are applied independently before pagination.
- Content is ordered by `publishedAt` newest first within each tier. This means an older followed-account item is intentionally shown before a newer public-discovery item.

For a guest, omit the `Authorization` header entirely. For a signed-in user, send `Authorization: Bearer <accessToken>`. Supplying an invalid or expired token returns `401`; it does not fall back to the guest feed.

Home responses use `Cache-Control: private, no-store` and `Vary: Authorization`.

The response uses `type` as a discriminator. Exactly one of `post` and `reel` is non-null:

Every item includes an `author` summary. `author.handle` is the username displayed by the client, and `author.avatarUrl` is nullable and temporary. `isFollowingAuthor` is `true` or `false` for authenticated requests and `null` for anonymous requests.

```json
{
  "items": [
    {
      "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
      "type": "POST",
      "author": {
        "accountId": "550e8400-e29b-41d4-a716-446655440000",
        "handle": "rajat_01",
        "displayName": "Rajat Singh",
        "avatarUrl": "https://s3.example.com/presigned-avatar",
        "viewerState": null
      },
      "isFollowingAuthor": true,
      "saved": true,
      "savedAt": "2026-07-26T12:30:00Z",
      "post": {
        "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
        "ownerAccountId": "550e8400-e29b-41d4-a716-446655440000",
        "caption": "Photos from tonight",
        "status": "PUBLISHED",
        "media": [
          {
            "id": "d3efb38c-d661-47b5-a9d2-a374b381a406",
            "position": 0,
            "contentType": "image/jpeg",
            "imageUrl": "https://s3.example.com/presigned-image"
          }
        ],
        "likeCount": 18,
        "liked": true,
        "commentCount": 4,
        "publishedAt": "2026-07-18T10:00:00Z",
        "createdAt": "2026-07-18T09:55:00Z",
        "saved": true
      },
      "reel": null,
      "publishedAt": "2026-07-18T10:00:00Z"
    },
    {
      "id": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
      "type": "REEL",
      "author": {
        "accountId": "550e8400-e29b-41d4-a716-446655440000",
        "handle": "rajat_01",
        "displayName": "Rajat Singh",
        "avatarUrl": "https://s3.example.com/presigned-avatar",
        "viewerState": null
      },
      "isFollowingAuthor": true,
      "saved": false,
      "savedAt": null,
      "post": null,
      "reel": {
        "id": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
        "ownerAccountId": "550e8400-e29b-41d4-a716-446655440000",
        "caption": "My first reel",
        "status": "PUBLISHED",
        "playbackUrl": "https://s3.example.com/presigned-video",
        "thumbnailUrl": "https://s3.example.com/presigned-thumbnail",
        "durationSeconds": 28,
        "likeCount": 10,
        "liked": false,
        "commentCount": 3,
        "publishedAt": "2026-07-18T09:50:00Z",
        "createdAt": "2026-07-18T09:30:00Z",
        "moderationReason": null,
        "saved": false
      },
      "publishedAt": "2026-07-18T09:50:00Z"
    }
  ],
  "nextCursor": "MHwyMDI2LTA3LTE4VDA5OjUwOjAwWg"
}
```

Frontend routing:

- `type: "POST"`: render `post.media` in the home feed.
- `type: "REEL"`: render the reel preview; when selected, navigate to the dedicated reels screen using `id`, then use `GET /api/v1/content/{contentId}` or `/api/v1/feeds/reels`.

For the next page, pass `nextCursor` back unchanged as `cursor`:

```http
GET /api/v1/feeds/home?limit=20&cursor=MHwyMDI2LTA3LTE4VDA5OjUwOjAwWg
Authorization: Bearer <same-optional-accessToken>
```

The Home cursor is opaque and includes the ranking tier; the frontend must not parse or generate it. `nextCursor` is `null` when `items` is empty. Keep authentication consistent while paging because changing users changes the followed-content tier.

### Profile posts

```http
GET /api/v1/profiles/creator_01/posts?limit=20&before=2026-07-15T10:00:00Z
Authorization: Bearer <optional-accessToken>
```

Returns only that profile's published image posts. Use this endpoint for the Posts tab on both the current user's profile and another user's profile.

### Profile reels

```http
GET /api/v1/profiles/creator_01/reels?limit=20&before=2026-07-15T10:00:00Z
Authorization: Bearer <optional-accessToken>
```

Returns only that profile's published reels. Use this endpoint for the Reels tab. It returns the same `FeedResponse` shape as `/api/v1/feeds/reels`.

Profile content visibility rules for both endpoints:

- A public profile can be viewed without authentication.
- A private profile can be viewed by its owner or an account that already follows it.
- Other private-profile requests return `404`, so the API does not reveal hidden content.
- If the viewer is signed in, send the bearer token even when the profile is public so the same call also works for private profiles the viewer may access.

The Posts endpoint returns `PostFeedResponse`:

```json
{
  "items": [
    {
      "id": "b64f5eb5-e05d-494b-bb65-c5896bc0af70",
      "ownerAccountId": "550e8400-e29b-41d4-a716-446655440000",
      "caption": "Photos from tonight",
      "status": "PUBLISHED",
      "media": [
        {
          "id": "d3efb38c-d661-47b5-a9d2-a374b381a406",
          "position": 0,
          "contentType": "image/jpeg",
          "imageUrl": "https://s3.example.com/presigned-image"
        }
      ],
      "likeCount": 18,
      "commentCount": 4,
      "publishedAt": "2026-07-15T10:00:00Z",
      "createdAt": "2026-07-15T09:55:00Z"
    }
  ],
  "nextCursor": "2026-07-15T10:00:00Z"
}
```

For profile Posts/Reels pagination, pass `nextCursor` as the next request's `before` value. These two endpoints use ISO-8601 timestamp cursors; Home alone uses its opaque `cursor` parameter.

Frontend profile-screen flow:

```text
GET /profiles/{handle}
  -> render profile header and read isPublic
  -> GET /profiles/{handle}/posts?limit=20 for the Posts tab
  -> GET /profiles/{handle}/reels?limit=20 for the Reels tab
  -> pass each tab's nextCursor as its own before value
```

Keep separate pagination state for the Posts and Reels tabs. Do not reuse the Home cursor for either profile endpoint.

## Story endpoints

Stories contain one image or video and expire exactly 24 hours after posting. For uploaded media, posting time is when the upload-completion endpoint accepts the object. For a story created directly from an existing post, reel, or story, posting time is the create request. Expiry is enforced by live-feed and direct-story reads; no cleanup scheduler is required for correctness. Successfully published rows remain available to their author through the archive, and selected archived rows can be exposed again through highlights.

Story statuses are `UPLOADING`, `TRANSCODING`, `PUBLISHED`, `FAILED`, `TAKEN_DOWN`, and `DELETED`.

Visibility values:

- `EVERYONE`: visible to anonymous and authenticated viewers only when the creator profile is public; for a private profile it is limited to the owner and active followers.
- `FOLLOWERS`: visible to the owner and accounts with an active viewer-to-owner follow.
- `CLOSE_FRIENDS`: visible to the owner and active accounts currently in the owner's close-friends list. If the creator profile is private, the viewer must also actively follow the creator.

Tagging a user does not grant story access. Removing a follow or close-friend membership immediately removes access to still-active restricted stories.

Profile privacy is always the outer visibility boundary. A story-level setting can narrow access but cannot make a private account's story public.

### Create an uploaded story

```http
POST /api/v1/stories
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "visibility": "CLOSE_FRIENDS",
  "caption": "Backstage",
  "locationId": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
  "hideComments": false,
  "hideLikeCount": false,
  "hideShareCount": false,
  "media": {
    "fileName": "backstage.mp4",
    "contentType": "video/mp4",
    "sizeBytes": 15728640
  },
  "linkedContentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "taggedAccountIds": [
    "6feea895-9b90-477d-a50b-1d9e77f0944d"
  ]
}
```

`media` accepts one JPEG, PNG, WebP, MP4, QuickTime, or WebM object. Default limits are 20 MiB for an image and 500 MiB for a video. `caption` is nullable and limited to 2,200 characters. `locationId` is optional; when present it must be a valid Groovsta location returned by the location APIs. `hideComments`, `hideLikeCount`, and `hideShareCount` default to `false`; send `true` for the creator toggles enabled during creation. At most 20 distinct, active accounts may be tagged.

`linkedContentId` is optional when media is uploaded. When supplied, it must identify a `PUBLISHED` post or reel owned by the creator or by another active account with a public profile. The response exposes its type so the app can navigate to the correct screen. Private-profile content cannot be republished through another account's story.

`linkedStoryId` is also optional and is used to reshare an existing story. It is mutually exclusive with both `media` and `linkedContentId`; existing requests that omit it are unchanged.

Returns `StoryCreateResponse`:

```json
{
  "story": {
    "id": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
    "author": {
      "accountId": "550e8400-e29b-41d4-a716-446655440000",
      "handle": "rajat_01",
      "displayName": "Rajat Singh",
      "avatarUrl": null,
      "viewerState": null
    },
    "mediaType": "VIDEO",
    "visibility": "CLOSE_FRIENDS",
    "status": "UPLOADING",
    "caption": "Backstage",
    "mediaUrl": null,
    "thumbnailUrl": null,
    "durationSeconds": null,
    "failureReason": null,
    "moderationReason": null,
    "commentsEnabled": true,
    "hideComments": false,
    "hideLikeCount": false,
    "hideShareCount": false,
    "likeCount": 0,
    "likeCountDisplay": "0",
    "liked": false,
    "commentCount": 0,
    "commentCountDisplay": "0",
    "shareCount": 0,
    "shareCountDisplay": "0",
    "linkedContent": {
      "contentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
      "type": "REEL"
    },
    "taggedAccounts": [
      {
        "accountId": "6feea895-9b90-477d-a50b-1d9e77f0944d",
        "handle": "diya",
        "displayName": "Diya Sharma",
        "avatarUrl": null,
        "viewerState": null
      }
    ],
    "location": {
      "id": "31d7d1e4-1f8c-40e6-86c2-50713d493777",
      "provider": "OLA",
      "providerPlaceId": "ola-platform:5000039498427",
      "name": "Koramangala",
      "formattedAddress": "Koramangala, Bengaluru, Karnataka, India",
      "latitude": 12.9352,
      "longitude": 77.6245,
      "city": "Bengaluru",
      "state": "Karnataka",
      "country": "India",
      "postalCode": null
    },
    "publishedAt": null,
    "expiresAt": null,
    "expired": false,
    "createdAt": "2026-07-24T10:00:00Z",
    "hasSeen": true,
    "viewerState": {
      "isOwner": true,
      "canDelete": true
    }
  },
  "upload": {
    "objectKey": "videos/stories/c22aed66-f64e-4a3c-a387-42dc1cd38e10/source/upload.mp4",
    "uploadUrl": "https://s3.example.com/presigned-put",
    "uploadExpiresAt": "2026-07-24T10:15:00Z"
  }
}
```

Upload bytes directly to `upload.uploadUrl` using the exact request `Content-Type`.

### Complete a story upload

```http
POST /api/v1/stories/{storyId}/uploads/complete
Authorization: Bearer <accessToken>
```

```json
{
  "objectKey": "videos/stories/c22aed66-f64e-4a3c-a387-42dc1cd38e10/source/upload.mp4"
}
```

The key must exactly match the create response and must exist in S3. Completion starts the 24-hour lifetime:

- Image story: becomes `PUBLISHED` immediately.
- Video story: becomes `TRANSCODING`; the Docker FFmpeg worker automatically changes it to `PUBLISHED`.
- Transcode failure: becomes `FAILED` and is visible only to its owner through direct retrieval while it remains within its lifetime.

### Share an existing post or reel

Omit `media` and supply a published `linkedContentId`:

```json
{
  "visibility": "FOLLOWERS",
  "caption": "Watch the full performance",
  "media": null,
  "linkedContentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "taggedAccountIds": []
}
```

The content may belong to the caller or another active account with a public profile. The first image of a post or the published video/thumbnail of a reel becomes the story media. The response is immediately `PUBLISHED`, `upload` is `null`, and `linkedContent.type` is `POST` or `REEL`. If the linked content is later taken down, its owner becomes inactive, or its profile becomes private, a media-borrowing story is no longer returned to other viewers.

### Reshare an existing story

Use the same create endpoint, omit `media` and `linkedContentId`, and supply `linkedStoryId`:

```json
{
  "visibility": "FOLLOWERS",
  "caption": "You need to see this",
  "media": null,
  "linkedContentId": null,
  "linkedStoryId": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
  "taggedAccountIds": []
}
```

The source story must still be active and `PUBLISHED`. A caller may reshare their own visible story. Another creator's story can be reshared only when that creator is active, their profile is public, and the source visibility is `EVERYONE`; `FOLLOWERS` and `CLOSE_FRIENDS` stories cannot be republished by another account. Resharing an existing reshare is flattened to the original source so availability and expiry cannot be extended through a chain.

The new story is immediately `PUBLISHED`, receives its own 24-hour lifetime, and returns `upload: null`. Its additive response field is:

```json
{
  "linkedStory": {
    "storyId": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
    "available": true,
    "unavailableMessage": null
  }
}
```

The reshare never extends the source story's media availability. When the source expires, is deleted/taken down, or otherwise becomes unavailable, the reshare disappears from other viewers' feeds and direct reads. The account that created the reshare can still see its record in the archive, with `mediaUrl` and `thumbnailUrl` set to `null` and:

```json
{
  "linkedStory": {
    "storyId": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
    "available": false,
    "unavailableMessage": "Story not available anymore"
  }
}
```

### Read and delete a story

```http
GET /api/v1/stories/{storyId}
Authorization: Bearer <optional-accessToken>
```

Returns `StoryResponse` only while active and visible. Hidden, deleted, expired, or unavailable link-only stories return `404`. If an Authorization header is supplied it must be valid or the response is `401`. Responses use `Cache-Control: private, no-store` and `Vary: Authorization`.

`StoryResponse.hasSeen` is viewer-specific:

- `false` for an anonymous request.
- `true` for the story owner.
- For another authenticated viewer, `true` only after that viewer has marked the story as seen.

Reading `GET /api/v1/stories/{storyId}` does not automatically mark it seen. The frontend decides when the story has been displayed sufficiently and calls:

```http
POST /api/v1/stories/{storyId}/views
Authorization: Bearer <accessToken>
```

The operation is idempotent and first verifies that the story is active and visible to the viewer. It returns:

```json
{
  "storyId": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
  "hasSeen": true,
  "seenAt": "2026-07-25T09:30:00Z"
}
```

Owners receive `hasSeen: true` without creating a separate view row. Hidden or expired stories return `404`.

The owner can see who has viewed the story:

```http
GET /api/v1/stories/{storyId}/views?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <accessToken>
```

This returns `ProfileSummaryPageResponse`, newest view first. Only the story owner may call it; other callers receive `404` so ownership is not disclosed. The owner's own implicit view is not included because no view row is created for it. `limit` defaults to 20 and is clamped to 1–100.

```http
DELETE /api/v1/stories/{storyId}
Authorization: Bearer <accessToken>
```

Only the owner can delete. Deletion is immediate and returns `204`.

### Story feed and profile stories

```http
GET /api/v1/feeds/stories?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <optional-accessToken>
```

Returns all currently visible stories using viewer-specific ordering:

1. Unseen stories first, ordered by `publishedAt` descending.
2. Seen stories afterward, also ordered by `publishedAt` descending.

Therefore the latest uploaded unseen story appears first, while previously viewed stories move to the end. Anonymous viewers receive only `EVERYONE` stories, all with `hasSeen: false`, ordered newest first. Authenticated viewers additionally receive followed and close-friend stories they may view, plus their own. The response is:

```json
{
  "items": [
    {
      "id": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
      "mediaType": "VIDEO",
      "visibility": "FOLLOWERS",
      "status": "PUBLISHED",
      "mediaUrl": "https://s3.example.com/temporary-story-video",
      "thumbnailUrl": "https://s3.example.com/temporary-story-thumbnail",
      "hasSeen": false,
      "likeCount": 7,
      "liked": true,
      "commentCount": 3,
      "linkedContent": {
        "contentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
        "type": "REEL"
      },
      "expiresAt": "2026-07-25T10:00:00Z"
    }
  ],
  "nextCursor": "opaque-cursor-or-null"
}
```

Items use the full `StoryResponse` shape; the abbreviated example highlights navigation fields. Pass `nextCursor` back unchanged. The opaque cursor includes the seen/unseen ordering tier, timestamp, and story ID. `limit` defaults to 20 and is clamped to 1–50.

```http
GET /api/v1/profiles/{handle}/stories?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <optional-accessToken>
```

Uses the same visibility, unseen-first ordering, authentication, pagination, cache, and response rules, restricted to one profile.

### Story archive

The authenticated author can load every successfully published story, including expired stories:

```http
GET /api/v1/me/stories/archive?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <accessToken>
```

This endpoint requires a valid bearer token. Missing, invalid, or expired credentials return `401`. It returns `StoryPageResponse`, sorted by `publishedAt` descending and then story ID descending. `limit` defaults to 20 and is clamped to 1–50; pass `nextCursor` back unchanged.

The archive includes both active and expired stories whose status is still `PUBLISHED`. It excludes unfinished uploads, transcode failures, manually deleted stories, and moderated/taken-down stories. Expiration does not change a database row to `DELETED`; `StoryResponse.expired` is `true` when `expiresAt` is at or before the response time. Archive media URLs use the normal temporary download lifetime rather than ending at the original story expiry.

A link-only story remains visible to its author in the archive even if the linked post/reel is later unavailable, but `linkedContent` is then `null` and that story cannot be added to a new highlight.

### Profile highlights

A highlight is a named, ordered collection of references to the author's existing stories. Media is not copied. The first story ID is the collection cover and the request order is the playback order.

Create a collection:

```http
POST /api/v1/highlights
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "name": "Collection 1",
  "storyIds": [
    "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
    "dbed17eb-6f31-4987-a0a2-4598ee085ee4",
    "ff14e67f-747f-4b79-9695-13d650ba4a96"
  ]
}
```

`name` is trimmed, required, and limited to 80 characters. `storyIds` must contain 1–100 distinct IDs. Every story must belong to the authenticated author, have status `PUBLISHED`, have usable published media, and—when it shares a post/reel—still reference published content. Active and expired stories are both accepted. Supplying an unavailable or non-owned story returns `404`.

The response is `StoryHighlightResponse`:

```json
{
  "id": "30866093-b88d-476c-a7c4-85ee410709dd",
  "name": "Collection 1",
  "stories": [
    {
      "id": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
      "status": "PUBLISHED",
      "expired": true,
      "mediaUrl": "https://s3.example.com/temporary-highlight-media",
      "expiresAt": "2026-07-25T10:00:00Z"
    }
  ],
  "createdAt": "2026-07-26T10:00:00Z",
  "updatedAt": "2026-07-26T10:00:00Z"
}
```

Each `stories` item uses the complete `StoryResponse` shape. The abbreviated example shows highlight-specific fields.

Rename a collection or replace its complete ordered story list:

```http
PUT /api/v1/highlights/{highlightId}
Authorization: Bearer <accessToken>
Content-Type: application/json
```

The request and response are the same shapes as creation. This is full replacement, not an append operation. Only the owner may update the collection; an unknown or non-owned ID returns `404`.

Delete only the collection:

```http
DELETE /api/v1/highlights/{highlightId}
Authorization: Bearer <accessToken>
```

Returns `204`. Referenced archive stories and their media are not deleted.

Render the highlight row on a profile with:

```http
GET /api/v1/profiles/{handle}/highlights?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <optional-accessToken>
```

This returns lightweight collection cards:

```json
{
  "items": [
    {
      "id": "30866093-b88d-476c-a7c4-85ee410709dd",
      "name": "Collection 1",
      "coverStory": {
        "id": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
        "expired": true,
        "mediaUrl": "https://s3.example.com/temporary-highlight-cover"
      },
      "storyCount": 3,
      "createdAt": "2026-07-26T10:00:00Z",
      "updatedAt": "2026-07-26T10:00:00Z"
    }
  ],
  "nextCursor": null
}
```

`coverStory` uses the complete `StoryResponse` shape and is the first currently visible story. `storyCount` is viewer-specific and counts only stories the caller may see. For the owner, a collection whose stories were subsequently deleted can have `coverStory: null` and `storyCount: 0`; empty collections are hidden from other viewers. Collections are newest first. `limit` defaults to 20 and is clamped to 1–50.

Load the selected collection with:

```http
GET /api/v1/highlights/{highlightId}
Authorization: Bearer <optional-accessToken>
```

This returns `StoryHighlightResponse` with visible stories in collection order. Expiry is ignored only because the story is referenced by a highlight. Profile privacy remains the outer boundary, and every story keeps its original audience:

- `EVERYONE` highlight stories are visible anonymously only for public profiles.
- `FOLLOWERS` highlight stories require an active follow.
- `CLOSE_FRIENDS` highlight stories require current close-friend membership; for a private profile, an active follow is also required.

Anonymous callers cannot access private-profile highlights. A caller without access receives `404`. If an Authorization header is supplied, it must be valid or the response is `401`. All archive and highlight reads use `Cache-Control: private, no-store` and `Vary: Authorization`.

An expired highlighted story is treated as visible by the existing views, likes, comments, liker-list, viewer-list, and report endpoints. Removing it from every highlight immediately closes those endpoints again after expiry. The ordinary `GET /api/v1/stories/{storyId}` and live story feeds continue to return only active stories.

### Story likes and comments

Every caller who is currently allowed to see an active story can read its `likeCount`, `commentCount`, liker list, comments, and replies. This includes anonymous callers for `EVERYONE` stories. `FOLLOWERS` and `CLOSE_FRIENDS` access is checked on every request, so losing access immediately hides engagement too. Supplied invalid/expired bearer tokens return `401`; callers without access receive `404`.

`StoryResponse.liked` is the single source of truth for whether the frontend fills the story heart. It is `false` anonymously, and story feed pages resolve it in one batch query. `StoryResponse.viewerState` contains only ownership and delete permissions.

Creators muted for `STORY` are excluded from the authenticated story feed. Muting does not revoke visibility, so their stories remain available through the creator's profile or a direct story URL.

Authenticated visible viewers can like or unlike:

```http
POST /api/v1/stories/{storyId}/likes
DELETE /api/v1/stories/{storyId}/likes
Authorization: Bearer <accessToken>
```

Both operations are idempotent and return:

```json
{
  "storyId": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
  "liked": true,
  "likeCount": 8,
  "likeCountDisplay": "8"
}
```

To open the likes screen:

```http
GET /api/v1/stories/{storyId}/likes?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <optional-accessToken>
```

This returns `ProfileSummaryPageResponse`, newest like first. Pass the opaque `nextCursor` unchanged; `limit` defaults to 20 and is clamped to 1–100.

### Story shares

To record a share:

```http
POST /api/v1/stories/{storyId}/shares
Authorization: Bearer <accessToken>
```

```json
{
  "channel": "OTHER"
}
```

Channels are `DIRECT_MESSAGE`, `STORY`, `EXTERNAL`, `OTHER`, and `COPY_LINK`. `COPY_LINK` is explicitly ignored for counting. All other channels count at most once per authenticated user per story. The response is:

```json
{
  "targetId": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
  "targetType": "STORY",
  "shared": true,
  "shareCount": 1200,
  "shareCountDisplay": "1.2K"
}
```

To open the story share-list screen:

```http
GET /api/v1/stories/{storyId}/shares?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <optional-accessToken>
```

This returns `ProfileSummaryPageResponse`, newest share first. The viewer must currently be allowed to see the story. The UI should render only `avatarUrl`, `displayName`/username, and `handle` if that is all the design needs.

Story comments use the same request, mention validation, one-level thread layout, pagination, comment-like response, and soft-deletion behavior documented for permanent content below. Replace `/api/v1/content/{contentId}` with `/api/v1/stories/{storyId}` in those comment paths. In a story `CommentResponse`, `storyId` contains the story UUID and `contentId` is `null`; for a post/reel comment, `contentId` is populated and `storyId` is `null`.

## Engagement endpoints

Engagement uses `contentId` and is available for both image posts and reels while the content is `PUBLISHED`.

### Like content

```http
POST /api/v1/content/{contentId}/likes
Authorization: Bearer <accessToken>
```

```json
{
  "contentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "liked": true,
  "likeCount": 11,
  "likeCountDisplay": "11"
}
```

The operation is idempotent.

### List users who liked content

```http
GET /api/v1/content/{contentId}/likes?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <optional-accessToken>
```

This endpoint applies equally to posts and reels and returns:

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

Results are newest-like first. Pass `nextCursor` unchanged; `limit` defaults to 20 and is clamped to 1–100. The content must still be `PUBLISHED`. An optional bearer token is accepted and, when supplied, must be valid.

### Unlike content

```http
DELETE /api/v1/content/{contentId}/likes
Authorization: Bearer <accessToken>
```

Returns `LikeResponse` with `liked: false`. The operation is idempotent.

### Share content

To record a share:

```http
POST /api/v1/content/{contentId}/shares
Authorization: Bearer <accessToken>
```

```json
{
  "channel": "OTHER"
}
```

Channels are `DIRECT_MESSAGE`, `STORY`, `EXTERNAL`, `OTHER`, and `COPY_LINK`. `COPY_LINK` is explicitly ignored for counting. All other channels count at most once per authenticated user per post/reel. Direct chat content-share messages automatically record `DIRECT_MESSAGE`, and creating a story from a post/reel automatically records `STORY`.

Returns:

```json
{
  "targetId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "targetType": "CONTENT",
  "shared": true,
  "shareCount": 1200,
  "shareCountDisplay": "1.2K"
}
```

To open the share-list screen:

```http
GET /api/v1/content/{contentId}/shares?limit=20&cursor={optionalOpaqueCursor}
Authorization: Bearer <optional-accessToken>
```

This returns `ProfileSummaryPageResponse`, newest share first. The content must still be `PUBLISHED`. The UI should render only `avatarUrl`, `displayName`/username, and `handle` if that is all the design needs.

### Add comment

```http
POST /api/v1/content/{contentId}/comments
Authorization: Bearer <accessToken>
```

```json
{
  "text": "@diya I agree with you",
  "replyToCommentId": "59ae198f-7891-4448-a646-99fdc8b8d7b8",
  "mentions": [
    {
      "accountId": "6feea895-9b90-477d-a50b-1d9e77f0944d",
      "start": 0,
      "endExclusive": 5
    }
  ]
}
```

`text` is nonblank and maximum 500 UTF-16 code units. `replyToCommentId` and `mentions` are optional. Mention positions use UTF-16 indexes, matching JavaScript and React Native. The backend verifies that the reply belongs to the same content and that every mention range contains the active account's current `@handle`. If the parent post, reel, or story has `commentsEnabled: false`, creating a top-level comment or reply returns `403` with reason `Comments are turned off`.

Top-level comments have `depth: 0`, point `threadRootId` to themselves, and have no `replyToCommentId`. All replies have `depth: 1`; replies to replies preserve the exact parent in `replyToCommentId` while remaining visually one level deep.

Returns:

```json
{
  "id": "2ca5a986-ef83-420f-9a8a-1150954b8d22",
  "contentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "storyId": null,
  "accountId": "550e8400-e29b-41d4-a716-446655440000",
  "threadRootId": "59ae198f-7891-4448-a646-99fdc8b8d7b8",
  "replyToCommentId": "59ae198f-7891-4448-a646-99fdc8b8d7b8",
  "depth": 1,
  "author": {
    "accountId": "550e8400-e29b-41d4-a716-446655440000",
    "handle": "rajat_01",
    "displayName": "Rajat Singh",
    "avatarUrl": "https://s3.example.com/temporary-avatar"
  },
  "text": "@diya I agree with you",
  "mentions": [
    {
      "accountId": "6feea895-9b90-477d-a50b-1d9e77f0944d",
      "handle": "diya",
      "start": 0,
      "endExclusive": 5
    }
  ],
  "replyCount": 0,
  "replyCountDisplay": "0",
  "likeCount": 4,
  "likeCountDisplay": "4",
  "createdAt": "2026-07-19T10:15:00Z",
  "isDeleted": false,
  "viewerState": {
    "canDelete": true,
    "canReply": true,
    "canLike": true,
    "liked": false
  }
}
```

### List comments

```http
GET /api/v1/content/{contentId}/comments?limit=20&cursor={optionalOpaqueCursor}
```

This returns only top-level comments, newest first:

```json
{
  "items": [],
  "nextCursor": null
}
```

Send an optional bearer token to populate `viewerState`; without it viewer actions and `liked` are false. `limit` defaults to 20 and is clamped to 1–100.

### List thread replies

```http
GET /api/v1/content/{contentId}/comments/{rootCommentId}/replies?limit=20&cursor={optionalOpaqueCursor}
```

Replies are returned oldest first so the conversation reads chronologically. The response uses the same `CommentPageResponse` shape.

### Like a comment

```http
POST /api/v1/content/{contentId}/comments/{commentId}/likes
Authorization: Bearer <accessToken>
```

Returns:

```json
{
  "commentId": "2ca5a986-ef83-420f-9a8a-1150954b8d22",
  "liked": true,
  "likeCount": 5,
  "likeCountDisplay": "5"
}
```

The operation is idempotent, including concurrent requests. The content must be published, and deleted comments cannot be liked.

### Unlike a comment

```http
DELETE /api/v1/content/{contentId}/comments/{commentId}/likes
Authorization: Bearer <accessToken>
```

Returns the same `CommentLikeResponse` with `liked: false`. The operation is idempotent.

### Delete own comment

```http
DELETE /api/v1/content/{contentId}/comments/{commentId}
Authorization: Bearer <accessToken>
```

Only the comment author can delete it. Deletion is idempotent and soft-deletes the text, mentions, and comment likes, returning `200` with an empty body. The response placeholder is `[deleted]`, so replies and stable thread relationships remain intact. A mismatched content item, comment, or author is exposed as `404`.

## Reporting endpoints

### Report a post or reel

```http
POST /api/v1/content/{contentId}/reports
Authorization: Bearer <accessToken>
```

```json
{
  "category": "HATE_OR_HARASSMENT",
  "details": "Contains targeted abusive language"
}
```

Categories:

```text
SEXUAL_CONTENT
VIOLENCE
HATE_OR_HARASSMENT
SPAM_OR_SCAM
INTELLECTUAL_PROPERTY
OTHER
```

`details` is nullable and maximum 1,000 characters. Only published content can be reported, and owners cannot report their own content. One report per account/content item is stored.

Returns:

```json
{
  "reportId": "48102289-4079-466b-b18a-bb8f087b1690",
  "contentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "status": "PENDING"
}
```

### Report a story

```http
POST /api/v1/stories/{storyId}/reports
Authorization: Bearer <accessToken>
```

The request uses the same `category` and optional `details` fields as permanent-content reports. The story must still be active and visible to the reporter, and owners cannot report their own stories. One report per reporter/story is stored.

```json
{
  "reportId": "48102289-4079-466b-b18a-bb8f087b1690",
  "storyId": "c22aed66-f64e-4a3c-a387-42dc1cd38e10",
  "status": "PENDING"
}
```

Story reports enter the same admin moderation queue as post/reel reports.

## Internal transcode-worker contract

These endpoints are not consumer-frontend APIs.

### Claim next job

```http
POST /api/v1/internal/transcode/jobs/claim
X-Transcode-Secret: <worker-secret>
```

Returns `204` when no job is queued. Otherwise returns:

```json
{
  "jobId": "cc154be4-13a6-4c06-a5e4-885f53cba734",
  "targetType": "REEL",
  "targetId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "reelId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
  "storyId": null,
  "sourceObjectKey": "videos/reels/7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12/source/upload.mp4",
  "outputPrefix": "videos/reels/7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12/published/"
}
```

### Complete transcode

`targetType` is `REEL` or `STORY`; exactly one of `reelId` and `storyId` is populated. The worker writes output objects to S3 before calling:

```http
POST /api/v1/internal/transcode/jobs/{jobId}/complete
X-Transcode-Secret: <worker-secret>
```

```json
{
  "publishedObjectKey": "videos/reels/7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12/published/video.mp4",
  "thumbnailObjectKey": "videos/reels/7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12/published/thumbnail.webp",
  "durationSeconds": 28
}
```

`thumbnailObjectKey` is nullable. `durationSeconds` must be positive. Output keys must use the assigned output prefix and exist in S3. Returns `200` empty body. A reel becomes `READY_TO_PUBLISH`; a video story becomes `PUBLISHED` while retaining the expiry set at upload completion.

The older `/api/v1/internal/transcode/reels/{reelId}/complete` callback remains available for reel-only worker compatibility.

### Fail transcode

```http
POST /api/v1/internal/transcode/jobs/{jobId}/fail?reason=Unsupported%20codec
X-Transcode-Secret: <worker-secret>
```

`reason` is required. Returns `200` empty body and changes the reel or story to `FAILED`. The older reel-specific failure callback remains available.

## Admin moderation contract

These endpoints are for the protected moderation frontend, not the consumer application.

### List pending reports

```http
GET /api/v1/admin/moderation/reports?limit=20
X-Moderation-Admin-Key: <admin-key>
```

`limit` defaults to 20 and is clamped to 1–100. Returns:

```json
[
  {
    "id": "48102289-4079-466b-b18a-bb8f087b1690",
    "contentId": "7b8fc7e7-dc4c-4ca2-970b-c8d9cb4aac12",
    "storyId": null,
    "reporterAccountId": "e23f2ad6-eb20-4d8b-860a-b9aa10f53c4d",
    "category": "HATE_OR_HARASSMENT",
    "details": "Contains targeted abusive language",
    "status": "PENDING",
    "resolution": "NONE",
    "moderatorReason": null,
    "createdAt": "2026-07-14T10:20:00Z",
    "reviewedAt": null
  }
]
```

Exactly one of `contentId` and `storyId` is populated. Use the matching direct content/story endpoint while reviewing.

### Resolve report

Dismiss without changing the content:

```http
POST /api/v1/admin/moderation/reports/{reportId}/decision
X-Moderation-Admin-Key: <admin-key>
```

```json
{
  "decision": "DISMISS",
  "reason": "No community-guideline violation"
}
```

Take down the content:

```json
{
  "decision": "TAKE_DOWN",
  "reason": "Violent content violating community guidelines"
}
```

Decision values: `DISMISS`, `TAKE_DOWN`. `reason` is nullable for dismissal, maximum 1,000 characters, and required for takedown.

Returns `200 ContentReportAdminResponse`. Takedown changes a post/reel or story to `TAKEN_DOWN`, removes it from feeds and public engagement, and resolves every pending report for that target. A taken-down story remains directly visible only to its owner until normal expiry so the owner can read `moderationReason`.

## Rewards

All reward endpoints require:

```http
Authorization: Bearer <accessToken>
```

### Get reward summary

```http
GET /api/v1/rewards/summary
```

Returns wallet balance, lifetime earned/claimed, pending coins, claimable coins, and recent achievements.

```json
{
  "walletBalance": 5,
  "lifetimeEarned": 8,
  "lifetimeClaimed": 5,
  "pendingCoins": 1,
  "claimableCoins": 2,
  "achievements": [
    {
      "id": "4f7df36c-1b0e-44d0-94fa-2bcfb8c3a111",
      "type": "REEL_UPLOAD",
      "title": "5 reels uploaded: 1 coin earned",
      "milestoneKey": "5",
      "currentCount": 5,
      "requiredCount": 5,
      "coinAmount": 1,
      "status": "PENDING",
      "claimable": false,
      "achievedAt": "2026-08-15T12:00:00Z",
      "eligibleAt": "2026-08-22T12:00:00Z",
      "claimedAt": null
    }
  ]
}
```

`status` can be `PENDING`, `CLAIMABLE`, or `CLAIMED`. `CLAIMABLE` is computed at read time when the buffer has passed and the maintained source count is still valid.

### Get achievement progress

```http
GET /api/v1/rewards/progress
```

Returns current counters and next milestone targets.

```json
{
  "items": [
    {
      "type": "REEL_SCROLL",
      "title": "Reels scrolled",
      "currentCount": 73,
      "nextRequiredCount": 100,
      "nextCoinAmount": 1,
      "completedMilestones": 0
    },
    {
      "type": "REEL_UPLOAD",
      "title": "Reel uploads",
      "currentCount": 5,
      "nextRequiredCount": 10,
      "nextCoinAmount": 1,
      "completedMilestones": 1
    }
  ]
}
```

### List achievements

```http
GET /api/v1/rewards/achievements?limit=25&cursor={nextCursor}
```

Returns:

```json
{
  "items": [],
  "nextCursor": null
}
```

Each item uses the same shape as `RewardAchievementResponse` from the summary endpoint.

### Claim coins

Claim all currently claimable rewards:

```http
POST /api/v1/rewards/claims
Content-Type: application/json
```

```json
{}
```

Claim one achievement:

```json
{
  "achievementId": "4f7df36c-1b0e-44d0-94fa-2bcfb8c3a111"
}
```

Returns:

```json
{
  "claimedCoins": 2,
  "walletBalance": 7,
  "claimedAchievements": []
}
```

Wallet balance increases only after claim succeeds.

### Record unique reel scroll

```http
POST /api/v1/rewards/reel-scrolls
Content-Type: application/json
```

```json
{
  "contentId": "6f3bcf6e-44a3-42e9-9bc4-f5920f8f6aaa"
}
```

Returns:

```json
{
  "contentId": "6f3bcf6e-44a3-42e9-9bc4-f5920f8f6aaa",
  "uniqueReelsScrolled": 100,
  "nextMilestoneAt": 200,
  "newlyEarnedAchievement": {
    "id": "0c79907f-70af-4ca4-9e66-a566adcaa111",
    "type": "REEL_SCROLL",
    "title": "100 reels scrolled: 1 coin earned",
    "milestoneKey": "100",
    "currentCount": 100,
    "requiredCount": 100,
    "coinAmount": 1,
    "status": "CLAIMABLE",
    "claimable": true,
    "achievedAt": "2026-08-15T12:00:00Z",
    "eligibleAt": "2026-08-15T12:00:00Z",
    "claimedAt": null
  }
}
```

The same reel counts only once per user.

Current reward rules:

| Type | Rule | Claim buffer |
|---|---:|---:|
| `SIGNUP_BONUS` | signup = 5 coins | none |
| `REEL_SCROLL` | 100 unique reels scrolled = 1 coin | none |
| `REEL_UPLOAD` | every 5 published reels = 1 coin | configurable, default 7 days |
| `FOLLOWER_MILESTONE` | 1,000 = 50, 5,000 = 100, 10,000 = 500 coins | configurable, default 7 days |
| `CONTENT_LIKE_MILESTONE` | 200 = 1, 500 = 2 coins | configurable, default 7 days |
| `STORY_LIKE_MILESTONE` | 200 = 1, 500 = 2 coins | configurable, default 7 days |

If a buffered reward is achieved, the backend creates an achievement and sends a `REWARD_MILESTONE_ACHIEVED` notification immediately. The claim button should stay disabled until `claimable=true`.

## Health endpoint

```http
GET /api/health
```

```json
{
  "status": "ok",
  "service": "groovsta-api"
}
```

Spring Actuator also exposes `GET /actuator/health` according to runtime management configuration.

## Frontend integration sequences

### New account

```text
signup
  -> save accessToken
  -> confirm first contact OTP
  -> request and confirm missing contact OTP
  -> account becomes ACTIVE
  -> fetch /api/v1/me
```

### Avatar

```text
POST /me/avatar/uploads
  -> PUT bytes to returned uploadUrl
  -> POST /me/avatar/uploads/complete with objectKey
  -> replace local profile with returned ProfileResponse
```

Do not call completion until S3 `PUT` returns a successful 2xx response.

### Reel creation

```text
POST /content/uploads with type=REEL and one video
  -> PUT source bytes to returned uploadUrl
  -> POST /content/{contentId}/uploads/complete
  -> poll GET /content/{contentId} as owner until READY_TO_PUBLISH or FAILED
  -> POST /content/{contentId}/publish
  -> display returned PUBLISHED reel
```

There is currently no push/websocket notification for transcode completion.

### Image post creation

```text
POST /content/uploads with type=POST and 1-10 image descriptions
  -> PUT each image to its returned uploadUrl
  -> POST /content/{contentId}/uploads/complete with every returned objectKey
  -> display returned PUBLISHED post
```

Do not call completion until every S3 `PUT` returns a successful 2xx response.

Keep the `contentId`, every `objectKey`, and each upload's `position` from the initiation response until completion succeeds. Do not save the presigned `uploadUrl`; request a new upload contract if it expires.

### Feed pagination

```text
GET /feeds/home?limit=20
  -> render items
  -> branch on item.type (POST or REEL)
  -> GET /feeds/home?limit=20&cursor={nextCursor}
  -> append unseen content IDs
```

### Direct chat

```text
GET /chat/conversations
  -> render otherUser, lastMessage, hasUnread
  -> GET /chat/conversations/{conversationId}/messages
  -> connect STOMP /ws/chat with Authorization in CONNECT
  -> subscribe /user/queue/chat-events
  -> send live messages to /app/chat.send
  -> mark the newest visible incoming message through /app/chat.read
  -> refetch REST history after reconnecting
```

There is no push notification or background-alert integration yet.

### Report and takedown

```text
consumer POST /content/{contentId}/reports
  -> moderation GET /admin/moderation/reports
  -> moderator reviews GET /content/{contentId}
  -> moderator DISMISS or TAKE_DOWN
```

### Rewards

```text
GET /rewards/summary
  -> show walletBalance, pendingCoins, claimableCoins
  -> show achievements/progress

POST /rewards/reel-scrolls when a user actually views a new reel in the reel player
  -> update uniqueReelsScrolled
  -> if newlyEarnedAchievement exists, show reward UI/notification state

GET /rewards/progress
  -> render achievement progress bars

POST /rewards/claims with {} or achievementId
  -> update wallet balance from response
```

Do not add pending coins to wallet on the frontend. Only `walletBalance` from the backend is spendable/owned balance.
