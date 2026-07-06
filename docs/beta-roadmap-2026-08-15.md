# Groovsta Beta Roadmap to 15 August 2026

## 1. Executive Summary

The architecture is strong for a production-scale rewards platform, but it is too wide for a safe beta by **15 August 2026** if we attempt the full v1 scope exactly as written.

The best path is:

- Keep the **domain boundaries** from the architecture.
- Ship a **beta slice** with the minimum end-to-end user loop.
- Push **KYC** and **coin redemption / payouts** to **R1** as requested.
- Treat the beta wallet as **earnings visibility only**: qualified views, provisional balances, basic referral accrual, no withdrawal flow.

## 2. Architecture Analysis

## What is good in the current architecture

- Clear separation between identity, content, money, and trust domains.
- Strong money model with a double-entry ledger.
- Good anti-fraud posture for a watch-to-earn product.
- Good long-term scale assumptions for 500k users and beyond.
- Sensible media pipeline using S3 + CloudFront + HLS.

## What makes the current plan risky for an August beta

- The logical scope is very large: 16 services, admin tools, moderation, fraud, wallet, payouts, referrals, search, notifications, analytics, and infra.
- The operational stack is heavy for a first release: EKS, Kafka, ClickHouse, OpenSearch, ArgoCD, CircleCI, MediaConvert, attestation, moderation, and PSP integration.
- The biggest beta risk is not just coding. It is integration risk across media, rewards, fraud, moderation, store policy, and release QA.

## Recommendation for beta architecture

Preserve the long-term domain boundaries, but do **not** split into microservices yet. For beta, the backend should be a **multi-module Maven Spring Boot project** deployed as a single backend application.

Recommended module structure:

1. `api-app` - main Spring Boot application, REST controllers, security, config
2. `identity-module` - auth, verification, sessions, profile, follow graph
3. `content-module` - media upload, reels, moderation gate, engagement
4. `feed-module` - reels feed, home feed, ranking rules
5. `rewards-module` - playback session, qualified view logic, provisional wallet, referrals
6. `trust-admin-module` - fraud rules, moderation review, reward controls, admin APIs
7. `shared-kernel` - common DTOs, events, errors, security helpers, audit, utilities

Shared platform for beta:

- `PostgreSQL`
- `Redis`
- `S3 + CloudFront`
- `SES + Twilio`
- `Push notifications`

Deployment model for beta:

- One deployable Spring Boot backend
- One PostgreSQL instance
- One Redis instance
- Async processing can stay in-process first, or use a lightweight queue where needed
- Internal package boundaries should mirror future service boundaries so extraction later is easier

Beta simplifications:

- Keep ledger and provisional balances inside the same backend, separated by module and package boundaries.
- Remove payout, KYC, PSP reconciliation, and withdrawal UI from beta.
- Keep referral attribution and qualification, but settle referral rewards into provisional balance only.
- Use rule-based fraud checks for beta; defer batch ML and deeper graph analysis to R1.
- Keep moderation for media publish blocking, but ship the smallest workable moderation console.
- Make search optional for beta; if time is tight, move it to R1.
- Keep reels as the primary content type; photo posts can be reduced or moved to R1 if schedule slips.

## 3. Beta Scope

## In scope for beta

- Email signup and login
- Email verification
- Phone verification
- Restricted to Active account gating
- 18+ declaration
- Basic profile
- Follow / unfollow
- Reel upload
- Reel transcode and publish pipeline
- Automated moderation gate for reel publishing
- Reels feed
- Basic home feed from followed users
- Like and basic comment support
- Qualified view detection
- Provisional earnings
- Earnings history
- Referral code and attribution
- Referral qualification
- Basic notifications
- Minimal admin tools for rewards config, moderation, and fraud review
- Observability, audit logs, and release dashboards

## Explicitly out of beta and moved to R1

- KYC
- Coin redemption
- Payout requests
- PSP integration and reconciliation
- Advanced fraud ML
- Full finance operations workflows
- Advanced search and hashtag discovery if schedule tightens
- Photo posts if schedule tightens

## 4. Beta Exit Criteria

Beta should ship only if these are true:

- Signup and verification work on Android and iOS test devices.
- Reel upload to publish flow is stable.
- Qualified view rules are deterministic on a tamper test set.
- Provisional wallet updates appear within the expected delay.
- Referral attribution and qualification work end to end.
- Moderation can block unsafe uploads before publish.
- Fraud rules can suppress obvious abuse cases.
- Crash, latency, and observability baselines are in place.

## 5. Delivery Roadmap

## Phase 0: Scope Lock and Foundations
**Target:** 6 July to 10 July

Goals:

- Freeze beta scope and mark all R1 items.
- Finalize the beta backend as a multi-module Maven Spring Boot project with clear module boundaries.
- Define API contracts for auth, reels, feed, rewards, referral, and admin.
- Stand up environments, CI/CD, database, object storage, and secrets management.
- Create release dashboard and issue tracking structure.

Deliverables:

- Beta scope sign-off
- Service boundaries and ERD baseline
- Infra skeleton
- GitHub project and issue backlog

## Phase 1: Identity and Core Social
**Target:** 11 July to 17 July

Goals:

- Implement signup, login, sessions, and dual verification.
- Support Restricted to Active lifecycle.
- Implement profile basics and follow / unfollow.
- Add device record capture and baseline abuse throttling.

Deliverables:

- Auth flows complete
- Profile and follow graph usable
- OTP and rate-limit protections working

## Phase 2: Reels and Feed
**Target:** 18 July to 24 July

Goals:

- Implement resumable upload, transcode, thumbnail generation, and publish states.
- Add moderation gate before publish.
- Build reels feed and simple home feed.
- Add like and basic comments.

Deliverables:

- End-to-end reel publish flow
- Feed usable on mobile
- Basic engagement metrics working

## Phase 3: Rewards Core
**Target:** 25 July to 31 July

Goals:

- Implement playback session tokens.
- Implement heartbeat validation and qualified view rules.
- Implement provisional accrual posting.
- Implement wallet read model and earnings history.

Deliverables:

- Qualified view engine
- Provisional wallet
- Budget guardrails and reward config

## Phase 4: Referral and Trust Controls
**Target:** 1 August to 7 August

Goals:

- Implement referral code generation and deep-link attribution.
- Implement referral qualification rules.
- Add basic fraud rules for duplicate device and suspicious velocity.
- Build minimal admin functions for moderation, fraud review, and reward control.

Deliverables:

- Referral end to end
- Fraud-lite controls
- Internal admin console for beta ops

## Phase 5: Stabilization and Beta Release
**Target:** 8 August to 15 August

Goals:

- Full end-to-end QA on real devices.
- Release hardening for uploads, feeds, and rewards.
- App store beta submission readiness.
- Monitoring, alerting, runbooks, and rollback plan.

Deliverables:

- Beta candidate
- UAT sign-off
- Monitoring and support readiness

## 6. Recommended Milestones

Use these GitHub milestones:

- `Beta Foundation`
- `Beta Identity`
- `Beta Reels and Feed`
- `Beta Rewards`
- `Beta Referral and Trust`
- `Beta Stabilization`
- `R1 Wallet Redemption`

## 7. Suggested GitHub Project Fields

Use these fields in GitHub Projects:

- `Ticket ID`: GRV-001 style tracking ID
- `Status`: Backlog, Ready, In Progress, Blocked, In Review, Done
- `Phase`: Foundation, Identity, Reels, Rewards, Trust, Stabilization, R1
- `Priority`: P0, P1, P2
- `Target Date`: date field
- `Release`: Beta, R1

## 8. Recommended Backlog Cut

## P0 for beta

- Auth and verification
- Reel publishing
- Feed
- Qualified views
- Provisional wallet
- Referral basics
- Moderation gate
- Admin minimum

## P1 for beta if time remains healthy

- Basic comments
- Search
- Improved notification preferences
- Better feed ranking

## R1 only

- KYC
- Coin redemption
- Payout workflows
- PSP reconciliation
- Tax handling
- Advanced fraud scoring

## 9. Importing This Roadmap into GitHub

GitHub Free is enough for this setup if you use:

- **GitHub Issues** for work items
- **GitHub Projects** for board, table, and roadmap views
- **Milestones** for beta phases and R1

Recommended import approach:

1. Create a GitHub Project.
2. Add the custom fields listed above.
3. Create the milestones listed above.
4. Import the backlog items from `docs/github-roadmap-import.csv` using the `gh` CLI helper script in `scripts/import-roadmap-to-github.sh`.
5. Add all created issues to the project.
6. Switch one view to **Roadmap** and group or filter by `Phase`, `Release`, or `Milestone`.

Note:

- GitHub Projects is strong for planning, but it is not Jira.
- The cleanest workflow is **Issues + Projects + Milestones**, not a single monolithic roadmap import.
- The incremental `Ticket ID` should stay stable even though GitHub will also assign its own issue numbers.
