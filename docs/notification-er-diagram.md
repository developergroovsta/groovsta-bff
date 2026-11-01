# Notification ER Diagram

This diagram reflects migrations through `V30__add_fcm_push_delivery.sql`.

```mermaid
erDiagram
    identity_accounts ||--o{ notifications : receives
    identity_accounts o|--o{ notifications : acts
    identity_accounts ||--o{ notification_push_registrations : owns
    identity_devices ||--o| notification_push_registrations : registers
    notification_push_registrations ||--o{ notification_push_deliveries : receives
    notifications o|--o{ notification_push_deliveries : dispatches

    notifications {
        UUID id PK
        UUID recipient_account_id FK
        UUID actor_account_id FK
        VARCHAR_40 notification_type
        VARCHAR_30 target_type
        UUID target_id
        UUID reference_id
        UUID context_id
        VARCHAR_280 preview
        TIMESTAMPTZ read_at
        TIMESTAMPTZ resolved_at
        TIMESTAMPTZ created_at
    }

    notification_push_registrations {
        UUID id PK
        UUID account_id FK
        UUID identity_device_id FK_UK
        VARCHAR_4096 registration_id
        VARCHAR_40 identifier_type
        VARCHAR_20 platform
        TIMESTAMPTZ disabled_at
        INTEGER failure_count
        TIMESTAMPTZ last_failure_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    notification_push_deliveries {
        UUID id PK
        UUID push_registration_id FK
        UUID recipient_account_id FK
        UUID notification_id FK
        VARCHAR_255 deduplication_key
        VARCHAR_50 message_type
        VARCHAR_160 title
        VARCHAR_320 body
        TEXT data_payload
        BOOLEAN high_priority
        BIGINT ttl_seconds
        VARCHAR_255 collapse_key
        VARCHAR_20 status
        INTEGER attempt_count
        TIMESTAMPTZ next_attempt_at
        TIMESTAMPTZ locked_until
        TIMESTAMPTZ sent_at
        VARCHAR_512 provider_message_id
        VARCHAR_1000 last_error
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }
```

`target_id` is the navigation target: profile account, content, story, conversation, or call. `reference_id` identifies the action that generated the notification, such as a follow request, like, mention, comment, message, or missed-call participant. `context_id` optionally identifies a nested navigation target, currently the exact comment for comment likes and mentions.

## Constraints and indexes

| Constraint / index | Purpose |
|---|---|
| Recipient FK with `ON DELETE CASCADE` | Deleting an account removes its inbox |
| Actor FK with `ON DELETE SET NULL` | Historical notifications remain if the actor is deleted |
| Unique `(notification_type, reference_id)` | Makes notification creation idempotent |
| `(recipient_account_id, created_at DESC, id DESC)` | Supports cursor-paginated inbox reads |
| Partial unread inbox index | Supports unread lists and badge counts |
| Unique push registration per identity device | Keeps one current Firebase registration per authenticated installation |
| Partial unique active `registration_id` | Prevents one active Firebase registration from belonging to multiple devices |
| Unique `(push_registration_id, deduplication_key)` | Makes enqueue/retry idempotent for each installation |
| Partial delivery claim index | Supports worker leasing for `PENDING`, `RETRY`, and expired `PROCESSING` rows |

`resolved_at` is currently used by actionable `FOLLOW_REQUEST` notifications. It is set when the request is accepted, rejected, or cancelled.

`notification_id` is nullable because call lifecycle pushes are durable deliveries but not inbox rows. Persisted social/chat/missed-call notifications populate it. Delivery statuses are `PENDING`, `PROCESSING`, `RETRY`, `SENT`, `SKIPPED`, and `DEAD`; expired processing leases can be reclaimed safely. A disabled registration is retained for audit/lifecycle purposes but no longer receives newly queued deliveries.
