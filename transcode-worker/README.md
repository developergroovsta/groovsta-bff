# Groovsta FFmpeg transcode worker

This standalone worker polls the Groovsta internal transcode API, downloads a claimed reel, video-story, or chat-video source from the private S3 bucket, creates a mobile-compatible MP4 and WebP thumbnail, uploads both outputs, and reports completion or failure to the API.

## Output

- Video: MP4, H.264, AAC, 30 fps, CRF 23, fast-start metadata, maximum width 1080 pixels.
- Thumbnail: WebP captured near the beginning of the transcoded video, maximum width 720 pixels.

The worker writes only to the `outputPrefix` assigned by the API:

```text
videos/reels/{reelId}/published/video.mp4
videos/reels/{reelId}/published/thumbnail.webp
videos/stories/{storyId}/published/video.mp4
videos/stories/{storyId}/published/thumbnail.webp
videos/chat/{accountId}/{uploadBatchId}/{attachmentId}/published/video.mp4
videos/chat/{accountId}/{uploadBatchId}/{attachmentId}/published/thumbnail.webp
```

## Required configuration

```text
GROOVSTA_API_URL=http://host.docker.internal:8080
GROOVSTA_TRANSCODE_CALLBACK_SECRET=development-transcode-secret
GROOVSTA_S3_BUCKET=groovsta-test-bucket
AWS_REGION=ap-south-1
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
```

Temporary AWS credentials also require `AWS_SESSION_TOKEN`.

For LocalStack, additionally configure:

```text
GROOVSTA_S3_ENDPOINT=http://host.docker.internal:4566
GROOVSTA_S3_PATH_STYLE_ACCESS=true
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
```

The worker must use the same bucket, region, S3 endpoint, and callback secret as `api-app`.

## Run with Docker Compose

Export the required AWS and Groovsta variables, then run from the backend repository root:

```bash
cp transcode-worker/.env.example transcode-worker/.env
# Fill in the S3 settings and AWS credentials in transcode-worker/.env.
docker compose --env-file transcode-worker/.env -f transcode-worker/compose.yaml up --build
```

Reel, story, and chat-video jobs share one queue and are processed oldest first. Stop the worker with `Ctrl+C`.

## Build and run directly

```bash
docker build -t groovsta-transcode-worker:local transcode-worker
docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -e GROOVSTA_API_URL=http://host.docker.internal:8080 \
  -e GROOVSTA_TRANSCODE_CALLBACK_SECRET \
  -e GROOVSTA_S3_BUCKET \
  -e AWS_REGION \
  -e AWS_ACCESS_KEY_ID \
  -e AWS_SECRET_ACCESS_KEY \
  -e AWS_SESSION_TOKEN \
  groovsta-transcode-worker:local
```

To claim no more than one job, append `--once`. To verify the container's media tools without connecting to Groovsta or S3:

```bash
docker run --rm groovsta-transcode-worker:local --check
```

## Required permissions

The worker's AWS principal needs `s3:GetObject` for `videos/reels/*/source/*`, `videos/stories/*/source/*`, and `videos/chat/*/source.*`, plus `s3:PutObject` for their corresponding `published/*` paths. The bucket should remain private.

This first worker is intended for one local worker process. The current backend claim operation does not yet provide atomic multi-worker leasing, abandoned-claim recovery, or automatic retries.
