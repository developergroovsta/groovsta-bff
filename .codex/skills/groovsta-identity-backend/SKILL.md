---
name: groovsta-identity-backend
description: "Use for Groovsta identity-module backend work: Kotlin/Spring Boot authentication, flexible email-or-phone signup, OTP verification, password reset, profile/social graph APIs, DTO splitting, JPA/Flyway schema changes, Swagger/OpenAPI docs, Docker Postgres runtime config, and production-readiness review."
---

# Groovsta Identity Backend

Use this skill when modifying or reviewing the Groovsta `identity-module` and related `api-app` runtime config.

## Repository scope

- Root Maven project: `groovsta-bff/`
- Runnable app: `api-app`
- Identity module: `identity-module`
- Identity Kotlin source: `identity-module/src/main/kotlin/com/groovsta/identity`
- Identity migrations: `identity-module/src/main/resources/db/migration`
- Runtime config: `api-app/src/main/resources/application.yml`

## Current structure conventions

- Put request DTOs in `com.groovsta.identity.dto.request`.
- Put response DTOs in `com.groovsta.identity.dto.response`.
- Keep one DTO per file.
- Put service interfaces in `com.groovsta.identity.service`, one interface per file.
- Put implementations in `com.groovsta.identity.service.impl`.
- Put Spring configuration/properties in `com.groovsta.identity.config`.
- Put auth resolver/security helpers in `com.groovsta.identity.security`.
- Put global web infrastructure in `com.groovsta.identity.web`.
- Keep controllers in `com.groovsta.identity.controller`.
- Keep JPA entities in `com.groovsta.identity.model`.
- Keep repository interfaces in `com.groovsta.identity.repository`.

## Validation and build

Use these commands from `groovsta-bff/`:

```bash
mvn -pl identity-module -am -DskipTests compile
mvn -pl api-app -am -DskipTests compile
```

Tests were intentionally removed from `identity-module/src/test`; do not recreate tests unless the user asks.

## Runtime assumptions

The app is Postgres-only for local runtime. `application.yml` defaults to:

```text
jdbc:postgresql://localhost:5432/groovsta
username: groovsta
password: groovsta
```

Expected local Postgres command:

```bash
docker run --name groovsta-postgres \
  -e POSTGRES_DB=groovsta \
  -e POSTGRES_USER=groovsta \
  -e POSTGRES_PASSWORD=groovsta \
  -p 5432:5432 \
  -d postgres:16
```

Run the app:

```bash
mvn -pl api-app -am spring-boot:run
```

Swagger:

```text
http://localhost:8080/swagger-ui.html
```

## Auth and verification flows

Signup supports email-first, phone-first, or both:

```json
{
  "email": "user@example.com",
  "phoneNumber": "+919876543210",
  "password": "CorrectHorse123",
  "handle": "user_01",
  "displayName": "User",
  "adultDeclaration": true
}
```

Account status stays `RESTRICTED` until both email and phone are verified. Only then should the account become `ACTIVE` and be eligible for earning points.

Signup/login require device headers:

```text
X-Device-Id: swagger-local-001
X-Platform: WEB
X-App-Version: 1.0.0
```

Use `AuthenticatedAccountResolver` for protected endpoints. Hide raw `Authorization` headers in Swagger and mark operations with bearer auth.

## OTP rules

- Never store raw OTPs.
- Store only `code_hash`.
- Send raw OTP only through `VerificationDeliveryService`.
- Use `VerificationPurpose.ACCOUNT_VERIFICATION` for signup/contact verification.
- Use `VerificationPurpose.PASSWORD_RESET` for password reset.
- Never allow account-verification OTPs to satisfy password-reset confirmation, or vice versa.
- Revoke active sessions after successful password reset.

## Database and Flyway

- Use Flyway migrations for schema changes.
- Do not rely on Hibernate DDL auto-generation; `ddl-auto` is `validate`.
- For Postgres 16, keep `flyway-database-postgresql` on the runtime classpath.
- Use nullable `identity_accounts.email` and unique nullable `identity_accounts.phone_number` to support flexible signup.
- Use explicit `@EntityScan` and `@EnableJpaRepositories` in identity configuration because entities/repositories live in a module outside `api-app`.

## Swagger/OpenAPI

- Springdoc is used via `springdoc-openapi-starter-webmvc-ui`.
- OpenAPI config lives in `com.groovsta.identity.web.OpenApiConfiguration`.
- Use `bearerAuth` security requirement for protected operations.
- Keep Swagger endpoint docs aligned when adding controller methods.

## Production readiness

When the user asks if this is production ready, or asks for deployment/security hardening, read:

- `references/production-readiness.md`
