# Identity Module ER Diagram

This diagram is based on `identity-module/src/main/resources/db/migration/V1__create_identity_schema.sql`.

```mermaid
erDiagram
    identity_accounts ||--|| identity_profiles : owns
    identity_accounts ||--o{ identity_devices : uses
    identity_accounts ||--o{ identity_sessions : has
    identity_devices  ||--o{ identity_sessions : creates
    identity_accounts ||--o{ identity_verification_challenges : receives
    identity_accounts ||--o{ identity_follows : follower
    identity_accounts ||--o{ identity_follows : followed

    identity_accounts {
        UUID id PK
        VARCHAR_320 email UK
        VARCHAR_100 password_hash
        VARCHAR_20 status
        TIMESTAMPTZ email_verified_at
        VARCHAR_32 phone_number
        TIMESTAMPTZ phone_verified_at
        TIMESTAMPTZ adult_declared_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    identity_profiles {
        UUID id PK
        UUID account_id FK_UK
        VARCHAR_30 handle UK
        VARCHAR_80 display_name
        VARCHAR_500 bio
        VARCHAR_2048 avatar_url
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    identity_devices {
        UUID id PK
        UUID account_id FK
        VARCHAR_255 device_identifier
        VARCHAR_20 platform
        VARCHAR_64 app_version
        VARCHAR_1024 last_user_agent
        TIMESTAMPTZ first_seen_at
        TIMESTAMPTZ last_seen_at
    }

    identity_sessions {
        UUID id PK
        UUID account_id FK
        UUID device_id FK
        VARCHAR_64 token_hash UK
        TIMESTAMPTZ expires_at
        TIMESTAMPTZ last_seen_at
        TIMESTAMPTZ revoked_at
        TIMESTAMPTZ created_at
    }

    identity_verification_challenges {
        UUID id PK
        UUID account_id FK
        VARCHAR_20 channel
        VARCHAR_320 destination
        VARCHAR_64 code_hash
        TIMESTAMPTZ expires_at
        INTEGER attempt_count
        TIMESTAMPTZ verified_at
        TIMESTAMPTZ invalidated_at
        TIMESTAMPTZ created_at
    }

    identity_follows {
        UUID id PK
        UUID follower_account_id FK
        UUID followed_account_id FK
        TIMESTAMPTZ created_at
    }
```

## Constraints and indexes

| Table | Constraint / index |
| --- | --- |
| `identity_accounts` | `email` is unique |
| `identity_profiles` | `account_id` is unique and references `identity_accounts(id)` with `ON DELETE CASCADE` |
| `identity_profiles` | `handle` is unique |
| `identity_devices` | `account_id` references `identity_accounts(id)` with `ON DELETE CASCADE` |
| `identity_devices` | unique pair: `(account_id, device_identifier)` |
| `identity_sessions` | `account_id` references `identity_accounts(id)` with `ON DELETE CASCADE` |
| `identity_sessions` | `device_id` references `identity_devices(id)` with `ON DELETE SET NULL` |
| `identity_sessions` | `token_hash` is unique |
| `identity_sessions` | index: `idx_identity_sessions_account_id(account_id)` |
| `identity_verification_challenges` | `account_id` references `identity_accounts(id)` with `ON DELETE CASCADE` |
| `identity_verification_challenges` | index: `idx_identity_verification_open(account_id, channel, destination, created_at DESC)` |
| `identity_follows` | `follower_account_id` references `identity_accounts(id)` with `ON DELETE CASCADE` |
| `identity_follows` | `followed_account_id` references `identity_accounts(id)` with `ON DELETE CASCADE` |
| `identity_follows` | check: `follower_account_id <> followed_account_id` |
| `identity_follows` | unique pair: `(follower_account_id, followed_account_id)` |
| `identity_follows` | index: `idx_identity_follows_followed(followed_account_id)` |
