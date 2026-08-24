# Object storage and reels flow

Groovsta stores media in a private S3 bucket. The database stores object keys; API responses contain short-lived presigned download URLs.

## S3 key layout

```text
images/avatars/{accountId}/{uploadId}.{extension}
images/posts/{postId}/{position}-{uploadId}.{extension}
images/stories/{storyId}/source/{uploadId}.{extension}
videos/reels/{reelId}/source/{uploadId}.{extension}
videos/reels/{reelId}/published/video.mp4
videos/reels/{reelId}/published/thumbnail.webp
videos/stories/{storyId}/source/{uploadId}.{extension}
videos/stories/{storyId}/published/video.mp4
videos/stories/{storyId}/published/thumbnail.webp
images/chat/{accountId}/{uploadBatchId}/{attachmentId}/source.{extension}
videos/chat/{accountId}/{uploadBatchId}/{attachmentId}/source.{extension}
videos/chat/{accountId}/{uploadBatchId}/{attachmentId}/published/video.mp4
videos/chat/{accountId}/{uploadBatchId}/{attachmentId}/published/thumbnail.webp
```

Clients cannot attach arbitrary URLs or keys. Completion endpoints verify ownership of the key and confirm that the object exists in S3.

## Avatar flow

1. Call `POST /api/v1/me/avatar/uploads` with the file name and content type.
2. Upload the file to the returned `uploadUrl` using HTTP `PUT` and the same `Content-Type`.
3. Call `POST /api/v1/me/avatar/uploads/complete` with the returned `objectKey`.
4. Profile responses expose a temporary presigned `avatarUrl`.

The completion call is required because the file is uploaded directly from the client to S3. The API does not receive the file and therefore cannot otherwise know when the upload finished or whether the client abandoned it. On completion, the API verifies that the key belongs to the account, checks that the S3 object exists, and only then updates the profile.

## Image post flow

1. The creator calls `POST /api/v1/content/uploads` with `type: POST`, a caption, and 1-10 image descriptions.
2. The client uploads each image directly to its returned S3 URL using the matching `Content-Type`.
3. The creator calls `POST /api/v1/content/{contentId}/uploads/complete` with every returned object key.
4. The API verifies ownership, the exact key set, and S3 existence for every image, then publishes the post.
5. Published posts appear in the unified `/api/v1/feeds/home` feed and in `/api/v1/profiles/{handle}/posts`.

Image posts do not enter the video transcode queue. API responses preserve carousel order using each media item's `position`.

## Reel flow

1. The creator calls `POST /api/v1/content/uploads` with `type: REEL` and one video description.
2. The client uploads directly to the returned S3 URL.
3. The creator calls `POST /api/v1/content/{contentId}/uploads/complete`; the reel enters `TRANSCODING`.
4. A transcode worker claims `POST /api/v1/internal/transcode/jobs/claim` using `X-Transcode-Secret`.
5. The worker reads the source key, writes video and thumbnail outputs under the supplied output prefix, and calls `POST /api/v1/internal/transcode/reels/{reelId}/complete`.
6. The reel becomes ready to publish as soon as transcoding succeeds.
7. The creator calls `POST /api/v1/content/{contentId}/publish`.
8. Published reels appear in `/api/v1/feeds/reels`, alongside image posts in `/api/v1/feeds/home`, and in `/api/v1/profiles/{handle}/reels` when profile visibility allows it.

The local FFmpeg implementation and its configuration are documented in [`transcode-worker/README.md`](../transcode-worker/README.md). Run one worker instance against the current polling API; atomic multi-worker leasing and abandoned-claim recovery are not implemented yet.

## Story flow

1. The creator calls `POST /api/v1/stories` with visibility, optional caption/link/tags, and either one media descriptor or an owned published `linkedContentId`.
2. A link-only story publishes immediately and reuses the linked content's first image or published video. It does not own or delete that object.
3. For uploaded media, the client uploads to the returned S3 URL and calls `POST /api/v1/stories/{storyId}/uploads/complete`.
4. Completion starts the exact 24-hour lifetime. An image publishes immediately; a video enters the shared transcode queue.
5. The worker claims a generic target job, writes story video/thumbnail outputs, and calls the job-ID completion endpoint.
6. Every story read checks status, expiry, owner account status, and the current `EVERYONE`, `FOLLOWERS`, or `CLOSE_FRIENDS` relationship.

Presigned story download URLs are never issued beyond the story's remaining lifetime.

## Chat attachment flow

1. The sender calls `POST /api/v1/chat/attachments/uploads` with a stable `clientMessageId`, recipient, optional caption/reply, and 1–10 image/video descriptors.
2. The API persists a pending upload batch and attachment UUIDs, then returns one private presigned PUT per attachment. It never exposes the generated object keys.
3. The client uploads every binary with the exact required `Content-Type` and calls `POST /api/v1/chat/attachments/uploads/{uploadBatchId}/complete`.
4. The API uses S3 HEAD to verify every object's presence, exact type, and exact byte length before atomically creating one `MEDIA` message.
5. Images become `READY` immediately. Videos become `PROCESSING` and join the same transcode queue used by reels and stories.
6. Worker completion/failure updates the attachment and publishes a viewer-specific chat `MESSAGE_UPDATED` event.

The database stores stable attachment IDs and private source/published object keys. `mediaUrl`, `thumbnailUrl`, and upload URLs are short-lived capabilities generated on demand, never identifiers. Identical initiation/completion retries are idempotent through `clientMessageId`; abandoned pending source objects are deleted best-effort after the configured retention period.

## Reporting and takedown flow

1. An authenticated user reports a published post or reel through `POST /api/v1/content/{contentId}/reports`.
2. Admin moderation lists pending reports through `GET /api/v1/admin/moderation/reports`.
3. Admin moderation submits `DISMISS` or `TAKE_DOWN` through `POST /api/v1/admin/moderation/reports/{reportId}/decision`.
4. Taken-down content receives `TAKEN_DOWN` status and immediately disappears from public and home feeds.

There is no pre-publish human approval gate. Moderation is triggered by user reports after publication.

## Required environment

For AWS, configure credentials through the standard AWS credential chain and set:

```text
GROOVSTA_S3_BUCKET
AWS_REGION
GROOVSTA_S3_IMAGE_PREFIX=images
GROOVSTA_S3_VIDEO_PREFIX=videos
GROOVSTA_TRANSCODE_CALLBACK_SECRET
GROOVSTA_MODERATION_ADMIN_KEY
```

For LocalStack, additionally set:

```text
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
GROOVSTA_S3_ENDPOINT=http://localhost:4566
GROOVSTA_S3_PATH_STYLE_ACCESS=true
```

For the AWS principal used by the API, keep object access limited to Groovsta's two media prefixes:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": [
        "arn:aws:s3:::groovsta-test-bucket/images/*",
        "arn:aws:s3:::groovsta-test-bucket/videos/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation", "s3:ListBucket"],
      "Resource": "arn:aws:s3:::groovsta-test-bucket",
      "Condition": {
        "StringLike": {
          "s3:prefix": ["images/*", "videos/*"]
        }
      }
    }
  ]
}
```

Attach this as an identity policy to the backend role/user, or grant the same principal through the bucket policy. Keep S3 Block Public Access enabled; mobile clients use short-lived presigned URLs rather than public objects.

Create the local bucket with:

```bash
aws --endpoint-url=http://localhost:4566 s3 mb s3://groovsta-local
```
