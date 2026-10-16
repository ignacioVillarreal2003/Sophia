# Sophia Authentication API

Production-ready authentication and authorization REST API built with Java 21, Spring Boot 3.5 and Spring Security 6.

## Features

- **RS256 JWTs + JSON Web Key Set** — tokens are signed with an asymmetric RSA key; consumers validate them against the public **`/.well-known/json-web-key-set.json`** endpoint. No shared secrets between services, keys can rotate via `kid`.
- **Machine-to-machine auth** — OAuth2 **client_credentials** grant (`POST /api/v1/authentication/token`, RFC 6749 form-encoded) backed by a service-client registry with scoped permissions, hashed secrets shown once, and secret rotation.
- **Refresh token rotation with reuse detection** — presenting a rotated token revokes the whole session family.
- **Google OAuth2** — API login with ID-token verification against Google JSON Web Key Set, browser `oauth2Login` flow with one-time code exchange, automatic registration, account linking/unlinking. ID tokens are never stored.
- **RBAC** — 2 seeded roles (`ADMIN`, `USER`), granular permissions, per-user direct grants, role hierarchy, `@PreAuthorize` on every admin endpoint.
- **Versioned API** — every business endpoint lives under `/api/v1/`, so contracts can evolve without breaking consumers.
- **Idempotency** — send an `Idempotency-Key` header on any `/v1` POST and retries replay the original response instead of duplicating resources (Redis-backed, 24h window, 409 on concurrent duplicates).
- **Resilience** — Resilience4j retry + circuit breaker (with slow-call detection and explicit timeouts) on Google JSON Web Key Set verification (breaker-open maps to 503).
- **Reliable messaging** — two transactional outboxes relayed to Kafka (at-least-once, ordered per user): user lifecycle events and notification commands. This service never sends email itself; it publishes commands that the notification service routes and renders. Scheduled jobs are guarded by ShedLock so they run on exactly one instance when scaled out.
- **Brute-force protection** — per-account lockout, per-IP failure budget (credential stuffing), timing-attack equalization (dummy BCrypt on unknown users), Redis rate limiting (strict budget on `/api/v1/authentication/**`).
- **Account lifecycle** — email verification, password reset/change, email change with confirmation, remember-me, soft delete, avatar upload.
- **Sessions** — device session list, revoke one / revoke all, logout / logout-all, Redis access-token blacklist.
- **Auditing** — append-only audit log (own transaction, survives rollbacks), login attempt trail, `created_by/updated_by/@Version` on entities.
- **Observability** — correlation IDs (`X-Request-Id`), OpenTelemetry distributed tracing (OTLP, opt-in), trace/span ids in logs, structured JSON logs (prod), liveness/readiness health groups (readiness gates on DB + Redis), Prometheus metrics, build/git info on `/actuator/info`.
- **Supply chain** — CycloneDX SBOM generated on every build; GitHub Actions CI runs tests + coverage gate, Trivy dependency scan and Trivy image scan.
- **API contract** — uniform `{success,data,message,timestamp,requestId}` envelope, standardized errors, full OpenAPI/Swagger.

## Quick start

```bash
cp .env.example .env          # fill in JWT_PRIVATE_KEY and Google credentials
docker compose up --build
```

| Service    | URL                                    |
|------------|----------------------------------------|
| API        | http://localhost:8080                   |
| Swagger UI | http://localhost:8080/swagger-ui.html   |
| MailHog    | http://localhost:8025                   |

Local dev without Docker for the app itself:

```bash
docker compose up postgres redis mailhog -d
./mvnw spring-boot:run          # dev profile is the default
```

## Tests

```bash
./mvnw test        # unit tests
./mvnw verify      # + integration tests (Testcontainers, requires Docker) + JaCoCo gate (72% bundle, 50% per security-critical class)
```

## Project structure

Organized in four layers: **api** (HTTP boundary), **application** (use cases /
business orchestration), **domain** (entities, DTOs, business rules) and
**infrastructure** (persistence, external systems).

```
src/main/java/com/sophia/authentication/
├── AuthenticationApplication.java
│
├── api/                     # Everything that faces the outside world (HTTP)
│   ├── config/              # SecurityConfig, WebConfig, OpenApiConfig
│   ├── controller/          # Auth, Users, Roles, Permissions, Sessions, AuditLogs
│   ├── exception/           # GlobalExceptionHandler (@RestControllerAdvice)
│   ├── filter/              # CorrelationIdFilter
│   ├── security/            # JWT filter, entry point, access-denied handler
│   │   ├── oauth2/          # OAuth2 login success/failure handlers, user service
│   │   └── ratelimit/       # RateLimitFilter
│   └── util/                # HttpRequestUtils
│
├── application/             # Business logic / use cases
│   ├── mapper/               # MapStruct mappers (entity <-> DTO)
│   ├── security/             # JwtService, CustomUserDetails(Service), SecurityUtils
│   └── service/              # Auth, Token, Password, EmailVerification, Google,
│                              # User, Role, Permission, Session, Audit, LoginAttempt...
│
├── domain/                  # Core model — no framework/infra dependencies
│   ├── config/               # AppProperties (typed configuration)
│   ├── dto/
│   │   ├── request/           # Validated request records
│   │   └── response/          # ApiResponse envelope, ErrorResponse, PageResponse...
│   ├── entity/                # User, Role, Permission, RefreshToken, UserSession...
│   │   └── enums/
│   ├── exception/             # ApiException hierarchy (business errors)
│   ├── util/                  # TokenHasher, AuditActions
│   └── validation/            # @StrongPassword
│
└── infrastructure/           # Persistence and external system adapters
    ├── config/                # JpaAuditingConfig, AsyncConfig, KafkaConfiguration (owns the user-events topic)
    ├── messaging/             # User lifecycle events + notification commands: payloads and outbox relays
    ├── repository/            # Spring Data JPA repositories + specifications
    ├── security/
    │   ├── oauth2/             # GoogleTokenVerifier, GoogleUserInfo (Google JSON Web Key Set)
    │   └── ratelimit/         # RedisRateLimiter
    └── service/                # EmailService (enqueues notification commands), StorageService (S3/MinIO),
                                 # TokenBlacklistService, OneTimeCodeService (Redis)

src/main/resources/
├── application.yml / application-dev.yml / application-prod.yml
├── logback-spring.xml
└── db/migration/    # Flyway: versioned schema, plus db/seed for the repeatable RBAC seed
```

## Key endpoints

| Area | Endpoints |
|------|-----------|
| Discovery | `GET /.well-known/json-web-key-set.json` — public keys for token validation (no auth) |
| Auth | `POST /api/v1/authentication/register`, `/api/v1/authentication/login`, `/api/v1/authentication/google`, `/api/v1/authentication/token` (client_credentials), `/api/v1/authentication/refresh`, `/api/v1/authentication/logout`, `/api/v1/authentication/logout-all`, `/api/v1/authentication/forgot-password`, `/api/v1/authentication/reset-password`, `/api/v1/authentication/change-password`, `/api/v1/authentication/verify-email`, `/api/v1/authentication/resend-verification`, `/api/v1/authentication/change-email`, `/api/v1/authentication/confirm-email-change`, `/api/v1/authentication/oauth2/exchange`, `/api/v1/authentication/google/link`, `DELETE /api/v1/authentication/google/unlink`, `GET /api/v1/authentication/me`, `DELETE /api/v1/authentication/account` |
| Users | `GET/POST /api/v1/users`, `GET/PUT/PATCH/DELETE /api/v1/users/{id}`, `PATCH /api/v1/users/{id}/roles|permissions|status|photo` |
| Roles | `GET/POST /api/v1/roles`, `GET/PUT/DELETE /api/v1/roles/{id}` |
| Permissions | `GET/POST /api/v1/permissions`, `GET/PUT/DELETE /api/v1/permissions/{id}` |
| Service clients | `GET/POST /api/v1/service-clients`, `GET/PUT/DELETE /api/v1/service-clients/{id}`, `POST /api/v1/service-clients/{id}/rotate-secret` |
| Sessions | `GET /api/v1/sessions`, `DELETE /api/v1/sessions/{id}`, `DELETE /api/v1/sessions` |
| Audit | `GET /api/v1/audit-logs` (filters: userId, action, status, from, to + pagination/sorting) |

### Consuming tokens from another service

1. Register a service client (`POST /api/v1/service-clients`, requires `SERVICE_CLIENT_MANAGE`) and store the returned secret.
2. Request a token: `POST /api/v1/authentication/token` with `grant_type=client_credentials&client_id=...&client_secret=...` (form-encoded).
3. Validate any token (user or service) by fetching `GET /.well-known/json-web-key-set.json` and verifying the RS256 signature — no shared secret needed. Spring consumers just set `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`.

## Talking to the notification service

This service sends no email of its own: every message is a **notification
command** published to `sophia.notifications.commands`, and the notification
service owns routing, preferences, templates and delivery. Two topics carry the
whole integration, both written through a transactional outbox so nothing is
lost when Kafka is down:

| Topic | Owner | Payload |
|-------|-------|---------|
| `sophia.notifications.commands` | notification | `NotificationCommandPayload` — `schemaVersion` (1), `messageId` (idempotency), `source`, `occurredAt`, `category` (`AUTHENTICATION_SECURITY`), `recipient` (`userId`, `email`, `displayName`, `locale`), `email` (`template`, `variables`) |
| `sophia.authentication.user-events` | authentication | `UserLifecycleEvent` — `eventType` (USER_CREATED / USER_SYNC / USER_UPDATED / USER_EMAIL_CHANGED / USER_DELETED), `userId`, `email`, `firstName`, `lastName`, `locale`, `occurredAt` |

Both records mirror the ones the notification service deserializes, field for
field; changing one without the other breaks the contract. Templates
(`security.email-verification`, `security.password-reset`,
`security.email-change-confirmation`, `security.password-changed`) and the
category code must exist in the notification catalogue — unknown values are
rejected into its dead letter topic instead of being delivered. Only the token
travels in `variables`: the frontend links are built on the notification side.

Messages are keyed by user id so one recipient keeps its ordering, and the
`X-Request-Id` of the originating HTTP request rides along as a Kafka header,
so a single correlation id spans authentication → Kafka → notification →
the delivered email.

## Configuration

All secrets come from environment variables — see `.env.example`. In `prod` the application **fails fast** if `JWT_PRIVATE_KEY` is missing or invalid; in `dev` an ephemeral key pair is generated (tokens don't survive restarts).

| Variable | Purpose |
|----------|---------|
| `JWT_PRIVATE_KEY` | RSA PKCS#8 key for RS256 signing (PEM / base64 PEM / base64 DER). Generate: `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \| base64 -w0` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis (rate limit, blacklist, OAuth2 codes, idempotency) |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth2 |
| `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` | S3/MinIO avatar storage |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Bootstrap admin account (`ADMIN_PASSWORD` is mandatory in prod) |
| `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS` | Frontend integration |
| `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_SECURITY_PROTOCOL` | Kafka broker (`localhost:29092` from the host, `sophia-kafka:9092` inside the `sophia` network) |
| `USER_EVENTS_ENABLED`, `USER_EVENTS_TOPIC`, `USER_EVENTS_PARTITIONS`, `KAFKA_TOPIC_REPLICAS` | Kafka user lifecycle events (transactional outbox); this service owns and creates the topic |
| `NOTIFICATIONS_ENABLED`, `NOTIFICATION_COMMANDS_TOPIC` | Notification commands published to the notification service (transactional outbox) |
| `TRACING_ENABLED`, `OTLP_ENDPOINT`, `TRACING_SAMPLING_PROBABILITY` | OpenTelemetry tracing export |

## Production deployment

- **TLS and client IPs** — terminate TLS at a reverse proxy that sets `X-Forwarded-For`/`X-Forwarded-Proto`; the app resolves the client IP through Tomcat's `RemoteIpValve` (only private-range proxies are trusted by default, override with `server.tomcat.remoteip.internal-proxies`). Rate limiting and the per-IP failure budget key off that resolved address.
- **Fail-fast configuration** — the `prod` profile refuses to start without `JWT_PRIVATE_KEY`, `MONITORING_PASSWORD`, `ADMIN_PASSWORD`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` and the database credentials.
- **State** — the service itself is stateless (JWT + Redis + Postgres + S3); scale horizontally at will. Scheduled jobs coordinate through ShedLock.
- **Data layer** — Postgres and Redis are single points of failure unless deployed managed/replicated with tested backups; Redis outages degrade gracefully (rate limiting and token blacklist fail open, sessions keep working).
- **Load testing** — a k6 baseline for `/login` and `/refresh` lives in `load/login-refresh.js`: `k6 run -e BASE_URL=http://localhost:8080 load/login-refresh.js`.

## CI

`.github/workflows/authentication-ci.yml` (repo root) runs on every push/PR touching this service: full `mvn verify` (unit + Testcontainers integration tests + JaCoCo gate (72% bundle, 50% per security-critical class)), SBOM artifact upload, Trivy filesystem scan, Docker image build and Trivy image scan (fails on HIGH/CRITICAL fixable vulnerabilities).
