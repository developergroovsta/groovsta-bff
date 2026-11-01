---
name: groovsta-identity-backend
description: "Use for Groovsta modular backend work across identity, content, feed, messaging, trust/admin, shared storage, and api-app: Kotlin/Spring Boot auth and OTPs, S3 media, feeds, direct chat, WebRTC calling, engagement, JPA/Flyway, Swagger, Docker Postgres, and production-readiness review."
---

# Groovsta Backend

Apply these project conventions when modifying or reviewing the Groovsta backend.

## Repository scope

- Use `api-app` as the runnable Spring Boot assembly and runtime-config owner.
- Use `identity-module` for accounts, sessions, verification, profiles, and follows.
- Use `content-module` for reels, transcode jobs, likes, comments, and user-facing report APIs.
- Use `feed-module` for public reels, optional-auth home-feed composition, and separate profile post/reel reads.
- Use `messaging-module` for direct conversations, message/read persistence, post/reel chat previews, audio/video call lifecycle, and STOMP WebSocket delivery/signaling.
- Use `trust-admin-module` for report persistence and admin moderation decisions.
- Use `shared-kernel` for narrow cross-module ports and shared S3 infrastructure.
- Keep migrations in each owning module under `src/main/resources/db/migration`; Flyway versions are global across all modules.

## Structure conventions

- Keep one DTO, service interface, and major model per file.
- Put request/response DTOs in module-specific `dto` packages; preserve identity's `dto.request` and `dto.response` split.
- Put service interfaces in `service` and implementations in `service.impl`.
- Put Spring configuration/properties in `config`, authentication helpers in `security`, and global web infrastructure in `web`.
- Put controllers, JPA entities, and repositories in `controller`, `model`, and `repository` respectively.
- Expose narrow interfaces from `shared-kernel` when two feature modules must collaborate. Avoid circular Maven dependencies.
- Configure every entity-owning module with explicit `@EntityScan` and `@EnableJpaRepositories`.

## Validation and build

Run from `groovsta-bff/`:

```bash
mvn -pl api-app -am -DskipTests compile
```

After deleting or renaming JPA entities, clean stale class output before runtime validation:

```bash
mvn -pl api-app -am -DskipTests clean install
```

Tests were intentionally removed; do not recreate them unless the user asks. Production readiness still requires tests.

## Runtime assumptions

- Use Postgres only; keep Hibernate `ddl-auto: validate` and Flyway enabled.
- Keep `flyway-database-postgresql` available for Postgres 16.
- Default local database is `jdbc:postgresql://localhost:5432/groovsta` with `groovsta/groovsta`.
- Run Swagger at `http://localhost:8080/swagger-ui.html`.
- Configure runtime values in `api-app/src/main/resources/application.yml` through environment variables.

## Identity rules

- Support email-first, phone-first, or both-contact signup.
- Keep accounts `RESTRICTED` until both email and phone are verified; only `ACTIVE` accounts earn points.
- Require `X-Device-Id`, `X-Platform`, and optional `X-App-Version` on signup, password login, and external login.
- Use `AuthenticatedAccountResolver` for bearer-protected endpoints and hide raw authorization parameters in Swagger.
- Store session-token and OTP hashes only; never store or log raw production OTPs.
- Separate `ACCOUNT_VERIFICATION` and `PASSWORD_RESET` challenges and revoke sessions after password reset.
- For Google login, accept only Firebase ID tokens verified by Firebase Admin with revocation checking, require `google.com` and a verified email, and exchange them for normal Groovsta sessions.
- Store external provider subjects in `identity_external_identities`; never store Google/Firebase ID tokens. Store only hashes for short-lived first-time onboarding registration tokens.
- Link an existing account only by a provider-verified normalized email, preserve suspended-account checks, and keep Google-only passwords nullable until a verified password-reset flow creates one.
- Treat Google as email verification only. First-time users must still declare 18+, choose a unique handle, and attach/verify a phone before becoming `ACTIVE`.
- Do not persist Google avatar URLs; use the private S3 avatar flow.

## S3 media rules

- Keep the bucket private. Store object keys in Postgres and return short-lived presigned URLs.
- Never accept arbitrary permanent avatar or reel URLs from clients.
- Segregate keys as:

```text
images/avatars/{accountId}/{uploadId}.{extension}
videos/reels/{reelId}/source/{uploadId}.{extension}
videos/reels/{reelId}/published/video.mp4
videos/reels/{reelId}/published/thumbnail.webp
```

- Use initiate/direct-upload/complete flows. On completion, verify key ownership and S3 existence before updating domain state.
- Keep S3 contracts/configuration in `shared-kernel`; keep avatar behavior in identity and reel behavior in content.

## Reel and transcode rules

- Preserve the state flow:

```text
UPLOADING -> TRANSCODING -> READY_TO_PUBLISH -> PUBLISHED
                                 |                  |
                               FAILED           TAKEN_DOWN
```

- Queue a transcode job only after confirming the source object exists.
- Keep worker endpoints under `/api/v1/internal/transcode` and authenticate them with the configured worker secret until production service authentication replaces it.
- Require worker output keys to remain under the reel's published prefix and verify output objects in S3.
- Do not require pre-publish human moderation. Successful transcoding makes a reel ready for creator publishing.

## Reporting and moderation rules

- Allow authenticated users to report published reels; do not allow self-reporting.
- Keep reports idempotent per reporter and reel.
- Use post-publish moderation: admins either `DISMISS` a report or `TAKE_DOWN` the reel.
- A takedown changes the reel to `TAKEN_DOWN`, removes it from feeds, and resolves all pending reports for that reel.
- Keep admin moderation under `/api/v1/admin/moderation`; the current admin key is a beta mechanism, not production authorization.

## Feed and engagement rules

- Store common post and reel fields in `content_items`, discriminated by `content_kind`; keep carousel images in `content_post_media`.
- Return only `PUBLISHED` reels owned by public profiles from the dedicated reels feed.
- Keep Home authentication optional. Rank the current account plus followed accounts first, then fill from other public profiles; preserve this tier in the opaque cursor.
- Keep profile content reads separate as `/profiles/{handle}/posts` and `/profiles/{handle}/reels`. Public profiles are visible to everyone; private profiles require the owner or an existing follower.
- Return a `type` discriminator for home items so clients can route reels to the dedicated reels experience.
- Treat likes as idempotent and enforce one like per account/reel in Postgres.
- Use generic `content_likes`, `content_comments`, and `trust_content_reports` records for both posts and reels.
- Permit engagement and reports only on published content, and allow users to delete only their own comments.

## Direct chat rules

- Keep direct-chat persistence in `messaging-module`; do not place conversations under identity controllers or feed services.
- Permit sends only between `ACTIVE` accounts. A public recipient accepts messages from any active account; a private recipient requires an active sender-to-recipient follow.
- Re-evaluate permission on every send. Privacy changes may stop new sends but must not erase participant history.
- Keep `clientMessageId` unique per sender and make retries idempotent.
- Persist post/reel references, not links or temporary URLs. Hydrate a native preview only while content remains published and visible to the viewer.
- Keep the non-null viewer-specific `liked` boolean on post, reel, story, feed, and shared-chat preview responses; return `false` anonymously and batch feed lookups.
- Use REST as reconnect/source-of-truth APIs and STOMP `/user` destinations for live in-app messages and read receipts.
- Keep edit authorization sender-only with a 10-second server-side window; implement unsend as a retained soft deletion.
- Keep replies within the same conversation and allow one replaceable emoji reaction per participant/message, including shared content messages.
- Treat delivered as recipient acknowledgement, seen as the monotonic read position, and online as authenticated WebSocket presence.
- Authenticate STOMP `CONNECT` using the same opaque bearer token as REST. Do not log the token.
- In-app notifications, FCM delivery, and private follow approval are implemented. Blocking, attachments, and group chat are still out of scope.

## Audio and video call rules

- Keep persisted call lifecycle and participant state in `messaging-module`; do not persist SDP or ICE candidates.
- Use WebRTC for media and authenticated STOMP user destinations for signaling. REST remains the history, reconnect, and lifecycle source of truth.
- Support `AUDIO` and `VIDEO` for direct calls and a beta WebRTC mesh capped at eight total participants.
- Enforce active-account, privacy/follow, participant membership, host, busy-account, invitation-expiry, and one-call-at-a-time rules server-side.
- Keep start retries idempotent by `(initiator_account_id, client_call_id)` and preserve the initial participant set separately from later invites.
- Keep each invitation/accept/decline/leave/miss state durable, promote a remaining participant when the host leaves, and end calls when fewer than two joined participants remain.
- Require heartbeat while joined, publish state only after commit, and send signaling only between two joined members of the same active call.
- Return STUN/TURN configuration from the authenticated ICE endpoint. Never hardcode TURN credentials in a frontend build.
- Persist `MISSED_CALL` notifications and deliver incoming/cancellation/lifecycle call events through the shared durable FCM boundary. Keep `eventId` identical across STOMP and push so clients can deduplicate.

## Notification and push rules

- Keep `notifications` as the durable inbox source of truth and publish committed changes to `/user/queue/notification-events` for foreground clients.
- Queue every persisted notification type for each active installation through `notification_push_deliveries`; never call Firebase inside a business transaction.
- Own client registration lifecycle through `PUT`/`DELETE /api/v1/notifications/push-token`, the authenticated identity device, logout, password-reset revocation, and Firebase invalid-registration responses.
- Never return or log raw registration tokens or service-account credentials. Use a mounted secret, base64 secret, or Application Default Credentials.
- Keep per-installation deduplication, atomic worker claims, bounded retry/backoff, leases, expiry, and invalid-token cleanup intact.
- Use high priority only for time-sensitive visible events. Keep normal social events at normal priority, sensitive previews disabled by default, and call TTL/collapse behavior aligned with the call state machine.

## Database and API rules

- Add a new Flyway migration for every schema change; never edit an applied migration.
- Keep migration versions unique across all modules. The current schema history reaches V30.
- Keep Springdoc operations, bearer requirements, internal headers, and request validation aligned with controller changes.
- Treat `docs/api-contract.md` as the frontend integration contract and update it with every controller, DTO, validation, enum, or status-code change.
- Update identity, media, chat, or flow documentation when persistence or endpoint flows change.

## Production readiness

Read `references/production-readiness.md` before production, deployment, security-hardening, AWS-media, transcode-worker, or moderation-readiness work.
