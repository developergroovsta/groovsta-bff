# Production readiness checklist

Use this checklist before shipping the Groovsta modular backend.

## Identity, OTP, and sessions

- Replace `LoggingVerificationDeliveryService` with production email/SMS providers; never log raw OTPs.
- Rate-limit OTP sends and attempts by account, destination, IP, and device; add resend cooldowns and provider failure callbacks.
- Prevent account enumeration in login, verification, and password-reset responses.
- Store verification peppers and all environment secrets in a managed secret store with rotation procedures.
- Formalize access/refresh-token behavior; add session listing/revocation and audit credential changes.
- Revoke sessions after password reset, suspension, credential compromise, and suspicious activity.
- Enforce `RESTRICTED` versus `ACTIVE` eligibility server-side in rewards and content workflows.
- Validate international phone numbers with a production-grade parser.
- Enable Google Authentication independently in every Firebase environment and verify that Android SHA-1/SHA-256, iOS bundle identifiers, web origins, OAuth consent-screen settings, and downloaded client configuration files match the released clients.
- Keep Firebase Admin credentials server-side, verify ID-token revocation and the `google.com` sign-in provider, and alert on wrong-project, revoked-token, account-link conflict, and unusual external-login failure rates without logging tokens.
- Rate-limit Google token exchange and onboarding by IP, device, provider subject, and email; add abuse detection around automatic verified-email account linking.
- Add retention cleanup for expired/consumed external-auth registrations and audit external identity link, unlink, relink, account deletion, and provider-email changes.
- Define an authenticated account-recovery path for unlinking/replacing Google identities; never allow unlinking the last usable login method without first adding another credential.

## S3 and media security

- Use a private, environment-specific S3 bucket with block-public-access, encryption, versioning policy, access logging, and least-privilege IAM.
- Restrict bucket CORS to approved app/web origins and required methods/headers.
- Keep presigned upload/download lifetimes short; never persist presigned URLs.
- Verify uploaded object size, MIME type, extension/signature, and ownership—not existence alone—before completion.
- Add image/video decoding checks and malware/content-safety scanning before media is exposed.
- Add lifecycle rules for abandoned source uploads, superseded avatars, failed transcodes, and temporary artifacts.
- Decide retention rules for source reels, transcoded outputs, moderation evidence, account deletion, and legal holds.
- Serve playback through a CDN with signed access if needed; invalidate or deny CDN access immediately after takedown.
- Add S3 failure metrics and alarms for access-denied, missing objects, throttling, and unexpected storage growth.

## Avatar flow

- Persist upload intents if stronger replay/expiry auditing is required; the current flow validates prefix and object existence.
- Normalize avatars to approved dimensions/formats and strip metadata before serving.
- Make replacement and cleanup idempotent so a failed old-object deletion does not fail profile updates.
- Add an orphan-cleanup process for initiated uploads that never complete.

## Transcode worker

- Deploy the Dockerized FFmpeg worker in each environment, or replace it with ECS/MediaConvert; the API process itself only orchestrates jobs and does not transcode bytes.
- Replace polling's simple first-row claim with atomic leasing/locking that supports concurrent workers.
- Add claim expiration, heartbeat/lease renewal, bounded retries, exponential backoff, timeout handling, and a dead-letter path.
- Make completion/failure callbacks idempotent and authenticated with IAM/mTLS or signed service credentials instead of a shared header secret.
- Validate duration, codecs, dimensions, frame rate, audio, output size, and all S3 output keys.
- Generate standardized renditions, thumbnails, and streaming manifests required by target clients.
- Add metrics and alerts for queue depth, claim age, transcode duration, failure rate, retries, and stuck jobs.

## Reports, moderation, and takedowns

- Replace `X-Moderation-Admin-Key` with authenticated admin identities, RBAC, MFA, and least-privilege roles.
- Build a protected moderation UI that shows the reported post or reel, report history, reporter context, and policy guidance.
- Add immutable audit logs recording moderator, decision, reason, timestamps, and prior content state.
- Define report categories, community-policy mappings, evidence retention, appeals, restoration, and repeat-offender workflows.
- Rate-limit reports and detect coordinated/abusive reporting.
- Add automated triage and escalation thresholds without reintroducing approval for every upload.
- Handle multiple moderators safely using optimistic locking or atomic report claiming.
- Ensure takedown blocks feed, direct playback, CDN access, rewards, and future engagement consistently.
- Define whether comments/likes remain, hide, or are deleted after takedown.

## Feed and engagement

- Add `content_id` tie-breaking to Home's tier-aware cursor and profile feed timestamp cursors to prevent duplicates or omissions when publication timestamps match.
- Define ranking, pagination consistency, blocked-user filtering, private/suspended-account behavior, and takedown propagation.
- Add follow-request spam controls, request expiry/cleanup, and abuse reporting; private profiles now require owner approval.
- Avoid per-item lookups and count queries at scale; batch home-feed subtype/media hydration and use denormalized counters with reconciliation.
- Add spam/rate limits for likes and comments, comment moderation, blocking, deletion policy, and auditability.
- Protect concurrent like/comment operations and verify idempotency under retries.

## Direct chat

- Add account blocking and enforce it in both directions before permitting new messages.
- Verify messaging authorization continues to require an accepted `FOLLOWING` edge for private recipients.
- Add per-account and per-recipient message rate limits, spam detection, abuse reporting, retention rules, and moderator access controls.
- Replace the in-memory STOMP broker with a production broker relay before running multiple API instances; configure inactive queue cleanup and cross-instance user destinations.
- Replace instance-local presence with Redis or broker-backed presence, add heartbeat/expiry handling, and define whether last-seen timestamps are exposed.
- Apply notification preferences, muted-conversation rules, quiet hours, and recipient rate limits to DM push eligibility; arbitrary text previews are disabled by default while fixed photo/video summaries remain safe to show.
- Restrict WebSocket origins in production and monitor connection counts, authentication failures, send failures, broker health, and reconnect rates.
- Define message deletion, account deletion, legal retention, export, encryption-at-rest, and whether end-to-end encryption is a future requirement.
- Protect edit-window, unsend, reaction replacement, and delivery acknowledgement operations against concurrent requests and retain moderation audit metadata.
- Cover concurrent first-message creation, duplicate client message IDs, out-of-order read receipts, reconnect catch-up, privacy changes, and unavailable shared content.
- Validate chat media by decoding the uploaded bytes rather than trusting client dimensions/duration, scan images and videos before recipient exposure, strip dangerous metadata, and reject codec/container mismatches.
- Add database or advisory locking for fully concurrent attachment initiation using the same `clientMessageId`; completion already locks its batch and remains idempotent.
- Add attachment upload/transcode rate limits, storage quotas, failed-video retry policy, stuck-job recovery, and alarms for abandoned uploads, S3 verification failures, and `PROCESSING` age.
- Define retention and deletion policy for unsent media, source files after successful transcode, failed files, moderation evidence, account deletion, and legal holds.

## Audio and video calls

- Deploy production TURN in every target region; STUN-only calls fail on restrictive carrier, enterprise, and symmetric-NAT networks.
- Replace static TURN credentials with short-lived per-user credentials, rotate secrets, and prevent credentials from appearing in logs or analytics.
- Move group video from the eight-person WebRTC mesh to an SFU such as LiveKit, mediasoup, Janus, or a managed service before increasing participant limits; add simulcast, active-speaker selection, and bandwidth adaptation.
- Integrate the implemented FCM call payloads with Android Telecom/ConnectionService and iOS CallKit, validate terminated-app/background behavior, and add call notification preferences. Keep call IDs only, short invite expiration, collapse/deduplication, and cancellation handling.
- Replace the in-memory STOMP broker with cross-instance signaling and ensure user destinations, ordering, reconnect, and call cancellation work across API replicas.
- Add call rate limits, invite spam controls, blocking in both directions, abuse reporting, user safety controls, and privacy-preserving group membership rules.
- Define call-log retention, account deletion, moderation/legal access, regional compliance, and whether signaling metadata requires encryption beyond TLS.
- Add WebRTC quality telemetry without SDP, IP-address, or media logging: setup time, ICE success, selected relay/direct path, packet loss, jitter, RTT, reconnects, and end reason.
- Handle mobile lifecycle transitions, network handoff, audio focus, Bluetooth/headset routing, permissions, interruptions, background execution, and crash recovery in clients.
- Add scheduler leadership or safe distributed cleanup, clock-skew tolerance, bounded transactions, and indexes/partitioning for high-volume call history.
- Test direct/group audio and video across iOS, Android, and web on Wi-Fi, cellular, VPN, IPv4/IPv6, symmetric NAT, device lock, app background, host departure, simultaneous accept/decline, and network loss.
- Run load tests for WebSocket signaling, call creation locks, heartbeat writes, cleanup, and the chosen TURN/SFU capacity before launch.

## Notifications

- Move the implemented PostgreSQL delivery outbox worker to a separately scalable worker or enforce scheduler capacity/leadership; alert on claim age, retries, dead deliveries, invalid tokens, and provider latency.
- Store Firebase credentials in a managed secret store, use separate projects per environment, rotate service-account access, and prevent credentials/registration IDs from entering logs or traces.
- Encrypt registration IDs at rest if required by the threat model and define token/account deletion retention and cleanup jobs for terminal delivery rows.
- Validate FCM delivery across Android, iOS/APNs-through-FCM, and web in foreground, background, terminated, offline, token-refresh, reinstall, logout, and password-reset scenarios.
- Add user preferences, quiet hours, muted-conversation behavior, aggregation, sensitive-preview controls, and per-event push eligibility.
- Add retention and cleanup policies for read notifications and resolved follow requests.
- Rate-limit follow requests and high-volume engagement events, and aggregate bursts such as many likes on the same content.
- Add real-time notification delivery through the production broker while retaining the persisted inbox as the source of truth.

## Database and migrations

- Use managed Postgres with TLS, restricted networking, backups, point-in-time recovery, monitoring, and restore drills.
- Review foreign keys, nullable unique constraints, indexes, retention, and deletion behavior across identity/content/trust tables.
- Enforce and monitor `content_items.content_kind` invariants so post rows cannot accidentally enter reel-only transcode workflows.
- Validate migrations against both a fresh database and an upgraded production-like database.
- Never edit applied migrations; keep version numbers globally unique across module JARs.
- Test rollback/forward-fix procedures before deployment; Flyway migrations are forward-only by default.
- Plan safe online migrations for large tables and indexes.

## API and authorization

- Replace shared internal/admin secrets with service and role authentication.
- Apply request-size limits, content-type validation, rate limits, abuse controls, and idempotency keys where clients retry writes.
- Keep Swagger accurate but disable or protect interactive Swagger in production as required.
- Review `GlobalExceptionHandler` for information leakage and log internal errors with correlation IDs.
- Enforce authorization in services, not only controllers or mobile UI.

## Observability and operations

- Add structured logs without OTPs, tokens, credentials, presigned URLs, or sensitive report details.
- Add traces around OTP providers, S3, transcode jobs, moderation decisions, and feed queries.
- Add metrics and alerts for auth failures, OTP abuse, media upload failures, transcode backlog, reports, takedowns, feed latency, and database saturation.
- Add readiness/liveness probes, graceful shutdown, deployment health checks, dashboards, runbooks, and incident ownership.
- Use environment-specific configuration and a container build/CI-CD workflow with dependency and image scanning.

## Testing before production

- Reintroduce unit, integration, API-contract, security, migration, and failure-path tests.
- Cover signup variants, both contact verifications, password resets, session revocation, and authorization boundaries.
- Cover avatar upload initiation/completion, forged keys, missing/wrong-size objects, replacement, and orphan cleanup.
- Cover reel state transitions, duplicate completion callbacks, worker concurrency, retry/failure paths, and S3 failures.
- Cover post and reel engagement, reports, self-report prevention, duplicate reports, dismissals, takedowns, multiple pending reports, and feed removal.
- Cover feed cursor stability, follows, likes/comments concurrency, and taken-down content access.
- Cover private follow request authorization and races, notification deduplication, unread counts, cursor pagination, actor deletion, and cross-account access.
- Use Testcontainers Postgres and an isolated S3-compatible test service or AWS test account; do not use H2 for persistence behavior.
- Run startup validation with all module JARs after clean builds to catch stale entities, repository scanning, and Flyway discovery issues.
