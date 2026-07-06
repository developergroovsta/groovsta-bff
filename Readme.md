# Groovsta Backend Scaffold

This repository now starts with a Maven multi-module Spring Boot backend scaffold aligned to the beta roadmap.

## Modules

- `api-app`: single deployable Spring Boot application
- `identity-module`: auth, verification, sessions, profile, social graph
- `content-module`: reels, media, moderation gate, engagement
- `feed-module`: home feed and reels feed logic
- `rewards-module`: qualified views, provisional wallet, referrals
- `trust-admin-module`: fraud rules, moderation review, admin controls
- `shared-kernel`: shared DTOs, utilities, errors, audit helpers

## Layout

The structure is intentionally a modular monolith for beta. Package and module boundaries mirror the roadmap so future extraction into services stays straightforward.

## Next Steps

- Add domain models and API contracts per module
- Wire persistence with PostgreSQL and Redis
- Add Spring Boot test slices and integration tests
- Introduce async job handling where the beta flows need it
