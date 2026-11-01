# Groovsta Backend Scaffold

This repository now starts with a Maven multi-module Spring Boot backend scaffold aligned to the beta roadmap.

## Modules

- `api-app`: single deployable Spring Boot application
- `identity-module`: auth, verification, sessions, profile, social graph
- `content-module`: reels, media upload/transcode orchestration, reporting, engagement
- `feed-module`: home feed and reels feed logic
- `messaging-module`: direct conversations, message history, read state, content sharing, live WebSocket chat
- `rewards-module`: qualified views, provisional wallet, referrals
- `trust-admin-module`: reel reports, takedown decisions, fraud rules, admin controls
- `shared-kernel`: shared DTOs, utilities, errors, audit helpers
- `transcode-worker`: standalone Dockerized FFmpeg worker for queued reels

## Layout

The structure is intentionally a modular monolith for beta. Package and module boundaries mirror the roadmap so future extraction into services stays straightforward.

## Documentation

See the centralized [documentation index](docs/README.md) for API contracts, ER diagrams, media flows, roadmap files, the project skill, and production readiness.

## Run locally with Firebase push and Google authentication

The local Firebase project is `groovsta-dev`. Keep its Admin SDK credential outside this repository at:

```text
~/.config/groovsta/firebase-service-account.json
```

The file must be readable only by its owner (`chmod 600`). Start PostgreSQL and then run:

```bash
./scripts/run-api-local-firebase.sh
```

The launcher enables Firebase Admin, FCM, and the Google token-exchange endpoints; supplies the project ID and credential path; clean-installs the current reactor modules locally; and starts `api-app`. Override `SERVER_PORT`, `GROOVSTA_FIREBASE_PROJECT_ID`, or `GROOVSTA_FIREBASE_CREDENTIALS_PATH` through environment variables when needed. Never commit an Admin SDK service-account JSON file.

The Firebase project currently has the Android package `com.groovsta.test` registered. Its validated client configuration is staged outside the repository at `~/.config/groovsta/google-services.json`. For the Expo frontend, copy it to the project root as `google-services.json` and keep `expo.android.googleServicesFile` set to `./google-services.json` in `app.json`; Expo prebuild then places the matching file in the generated Android app. The frontend must use the exact `com.groovsta.test` package, obtain a real FCM token, and register it through `PUT /api/v1/notifications/push-token`. Google login additionally requires enabling the Google provider in Firebase Authentication and configuring Android SHA-1/SHA-256 fingerprints before regenerating `google-services.json`.

## Next Steps

- Add domain models and API contracts per module
- Wire persistence with PostgreSQL and Redis
- Add Spring Boot test slices and integration tests
- Introduce async job handling where the beta flows need it
