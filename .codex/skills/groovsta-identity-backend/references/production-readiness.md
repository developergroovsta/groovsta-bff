# Production readiness checklist

Use this checklist before shipping the Groovsta identity module to production.

## OTP delivery

- Replace `LoggingVerificationDeliveryService` with real providers:
  - Email: SES, SendGrid, Postmark, or equivalent.
  - SMS/phone: Twilio, SNS, MessageBird, or equivalent.
- Never log raw OTPs in production.
- Add rate limits per account, destination, IP, and device.
- Add resend cooldowns.
- Add abuse monitoring for OTP floods and enumeration.
- Use provider callbacks/webhooks for delivery failure tracking where available.

## Secrets and cryptography

- Move `IDENTITY_VERIFICATION_PEPPER` to a secret manager.
- Rotate peppers/secrets through a documented operational process.
- Use separate secret values per environment.
- Consider HMAC-SHA-256 for OTP hashing instead of plain salted/peppered SHA-256.
- Keep BCrypt password hashing or move to Argon2id if the platform standardizes on it.

## Account enumeration and error handling

- Password reset request endpoints should avoid revealing whether an email/phone exists.
- Login and reset failures should return generic messages.
- Keep detailed reasons in internal logs only.
- Review `GlobalExceptionHandler` messages for information leakage.

## Authentication/session hardening

- Add refresh-token strategy or formalize current access-token-only sessions.
- Store session token hashes only.
- Revoke sessions after password reset, account suspension, and suspicious activity.
- Add device/session listing and revocation endpoints if product requires them.
- Add audit logs for login, logout, reset, verification, and credential changes.

## Authorization and product gating

- Keep `RESTRICTED` accounts blocked from point-earning workflows.
- Add explicit authorization checks in future rewards/content modules.
- Do not rely only on UI hiding; enforce eligibility server-side.

## Data model and migrations

- Review nullable unique constraints for production Postgres behavior.
- Add indexes for lookup paths:
  - `identity_accounts(email)`
  - `identity_accounts(phone_number)`
  - open verification challenge lookups by account, purpose, channel, destination.
- Review existing migrations against a fresh Postgres database and an upgraded database.
- Avoid editing already-applied migration files; create new migrations.

## Observability

- Add structured logs without secrets/OTPs/tokens.
- Add metrics for signup, login, verification, password reset, OTP send failure, invalid OTP attempts, and rate-limit hits.
- Add traces around external delivery providers.
- Add alerts for abnormal auth failure rates and OTP spikes.

## Validation and API contracts

- Document all request/response schemas in Swagger.
- Confirm Swagger examples for:
  - email-first signup
  - phone-first signup
  - both-contact signup
  - account verification
  - password reset
- Decide whether login should allow both email and phone or exactly one; current behavior expects exactly one.
- Validate phone numbers beyond regex if production requires country-specific rules.

## Infrastructure

- Run Postgres with managed backups, PITR, SSL, and restricted network access.
- Use environment-specific config, not hard-coded local defaults.
- Add container image build and deployment workflow.
- Add readiness/liveness probes.
- Keep `/actuator` exposure minimal and protected as needed.

## Testing before production

- Reintroduce tests before production:
  - DTO validation tests.
  - signup email-first, phone-first, both-contact flows.
  - email and phone verification.
  - password reset via email and phone.
  - session revocation after password reset.
  - Flyway migration validation against Postgres.
  - repository scanning and app startup.
- Add integration tests using Testcontainers Postgres, not H2.
