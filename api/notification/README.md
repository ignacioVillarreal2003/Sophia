# Sophia Notification Service

The microservice that centralizes every notification on the platform: an in-app
inbox with realtime delivery (SSE), email sending, and per-user notification
preferences. The other services never send anything themselves — they publish
**notification commands** to the Kafka topic this service owns, and this service
decides the routing (channels, preferences, template rendering) and the delivery.

## Stack

Java 21 · Spring Boot 3.5 · PostgreSQL + Flyway · Redis · Kafka · SMTP
(MailHog in dev) · Server-Sent Events · MapStruct · springdoc-openapi ·
Micrometer/OTel · JUnit 5 + Testcontainers

## Architecture

```
authentication ──outbox──▶ sophia.notifications.commands ─┐
document-workspace ──outbox──▶ (same topic)              ├──▶ notification-api
authentication ──outbox──▶ sophia.authentication.user-events ─┘        │
                                                       ┌──────────────┼──────────────┐
                                                       ▼              ▼              ▼
                                                  Postgres        SSE stream      SMTP
                                             (inbox, prefs,     (/notifications  (email_outbox
                                              contacts,           /stream)        + dispatcher)
                                              email outbox)
```

- **Its own database**: `sophia_notification` (Flyway). Nothing shared with the
  other services.
- **Command topic**: `sophia.notifications.commands` (6 partitions, keyed by
  recipient). Invalid messages go to the dead letter topic
  `sophia.notifications.commands.dlt`; transient errors are retried with
  exponential backoff. The user-events consumer gets the same treatment
  (`sophia.authentication.user-events.dlt`): no consumer retries forever, so a
  poison message cannot stall a partition. The replication factor of the topics
  this service creates comes from `KAFKA_TOPIC_REPLICAS` (1 in dev, 3 under the
  `prod` profile).
- **Idempotency**: every command carries a `messageId`; processed ones are
  recorded in `processed_messages` and duplicates are dropped.
- **Contact projection**: consumes `sophia.authentication.user-events`
  (USER_CREATED / USER_SYNC / USER_UPDATED / USER_EMAIL_CHANGED / USER_DELETED)
  to resolve email and name without synchronous calls back to authentication.
  USER_DELETED removes the user's notifications, preferences and contact.
- **Email**: rendering (templates plus links into the frontend) lives here.
  Emails are queued into `email_outbox` in the same transaction as the command,
  and a dispatcher with ShedLock, backoff retries and a circuit breaker sends
  them over SMTP (MailHog in dev). Each message is reserved, sent and marked in
  separate transactions: a database connection is never held open while talking
  to the SMTP server, and a failure mid-batch cannot undo the sends already done.
- **Realtime across instances**: notifications are published to Redis
  (`sophia:notification:stream:{userId}`) and each instance forwards to the SSE
  streams it holds. A user's stream can live on any replica while another one
  consumes the command.
- **Preferences**: global per-channel switches (`EMAIL` / `IN_APP`) plus
  per-category overrides. Categories flagged as mandatory
  (`AUTHENTICATION_SECURITY`) cannot be silenced and their emails carry no
  unsubscribe header.

## Command contract

JSON published to `sophia.notifications.commands`:

```json
{
  "messageId": "unique uuid (idempotency)",
  "occurredAt": "2026-07-27T12:00:00Z",
  "category": "AUTHENTICATION_SECURITY | DOCUMENT_WORKSPACE_WORKSPACE | DOCUMENT_WORKSPACE_SHARING | DOCUMENT_WORKSPACE_MENTION | DOCUMENT_WORKSPACE_COMMENT | FINANCE_WORKSPACE_BUDGET | FINANCE_WORKSPACE_GOAL | FINANCE_WORKSPACE_LIABILITY | SYSTEM",
  "actorId": "uuid of whoever caused it, optional",
  "attributes": {
    "severity": "INFO | SUCCESS | WARNING | ERROR (default INFO)",
    "priority": 0,
    "groupKey": "optional, max 200",
    "targetType": "optional, max 40",
    "targetId": "optional uuid",
    "expiresAt": "optional instant"
  },
  "recipient": {
    "userId": "uuid or null",
    "email": "explicit or null (resolved from the projection when absent)",
    "displayName": "optional"
  },
  "inApp": {
    "type": "MENTION",
    "title": "required, max 255",
    "body": "optional, max 2000",
    "data": { "workspaceId": "...", "artifactId": "...", "actorId": "..." }
  },
  "email": {
    "template": "security.password-reset | document-workspace.invitation | generic.notification | ...",
    "variables": { "name": "...", "token": "..." },
    "subject": "only for template-less emails",
    "htmlBody": "only for template-less emails"
  }
}
```

Rules: at least one of `inApp` / `email`; `inApp` requires `recipient.userId`;
`email` requires either `userId` or an explicit `email`. Mandatory categories
ignore the user's preferences.

The command is validated against the schema **before** touching the database: a
`severity` outside the allowed set, or a field longer than its column, is
rejected as an invalid command (it goes to the DLT with a message naming the
offending field) instead of surfacing as a retried constraint violation.

A future `scheduledAt` defers delivery: the in-app notification is stored but
does not appear in the inbox or get pushed over SSE until that time, and the
email waits in the outbox. `schemaVersion` allows the contract to be versioned;
today it is 1 and any other value is rejected rather than misinterpreted.

Available templates: `security.email-verification`, `security.password-reset`,
`security.email-change-confirmation`, `security.password-changed`,
`document-workspace.invitation`, `generic.notification`. The frontend links
(verify-email, reset-password, invitations) are built here from
`FRONTEND_BASE_URL`; producers only send the token.

The copy lives in `src/main/resources/templates/email/messages*.properties`, not
in the code: changing wording does not require touching Java. The language comes
from the command's `recipient.locale` or from the projected contact, falling
back to English; today `en` and `es` exist.

## API surface (v1)

All routes require a bearer token issued by the authentication service.

| Method | Route | What it does |
| --- | --- | --- |
| GET | `/api/v1/notifications` | Inbox with filters, sorting and pagination |
| GET | `/api/v1/notifications/groups` | Inbox collapsed by `groupKey` |
| GET | `/api/v1/notifications/unread-count` | Number of unread items |
| POST | `/api/v1/notifications/{id}/read` | Mark one as read |
| POST | `/api/v1/notifications/read-all` | Mark everything as read |
| POST | `/api/v1/notifications/groups/{groupKey}/read` | Mark a whole group as read |
| DELETE | `/api/v1/notifications/{id}` | Delete a notification |
| GET | `/api/v1/notifications/{id}/dispatches` | Delivery status per channel |
| GET | `/api/v1/notifications/stream` | Realtime SSE stream |
| GET | `/api/v1/notification-preferences` | Effective preferences |
| PUT | `/api/v1/notification-preferences` | Update preferences |

With elevated permissions (`*_UNRESTRICTED`, `NOTIFICATION_CATEGORY_*`):

| Method | Route | What it does |
| --- | --- | --- |
| GET | `/api/v1/users/{userId}/notifications` | Another user's inbox (`category` filter) |
| GET | `/api/v1/users/{userId}/notification-preferences` | Another user's preferences |
| PUT | `/api/v1/users/{userId}/notification-preferences` | Change them on their behalf |
| GET | `/api/v1/notification-categories` | Category catalogue (search, pagination) |
| POST | `/api/v1/notification-categories` | Create a category |
| PATCH | `/api/v1/notification-categories/{code}` | Update a category |
| DELETE | `/api/v1/notification-categories/{code}` | Delete a category |
| GET | `/api/v1/notifications/by-correlation/{id}` | Deliveries originated by one `X-Request-Id` |
| GET | `/api/v1/audit-log` | Trail of privileged actions |
| POST | `/api/v1/dead-letters/replay` | Reprocess messages from a DLT |

**Inbox filters**: `unreadOnly`, `category`, `type`, `severity`, `groupKey`,
`from`, `to` and `search` (free text over title and body). Sortable by
`createdAt`, `readAt`, `priority`, `severity`, `category`, `type` and `title`;
any other field returns 400. Newest first by default. Notifications scheduled
for the future and expired ones never come back from a read.

The SSE stream sends a keep-alive every `STREAM_HEARTBEAT_INTERVAL` (20s by
default) so proxies do not cut idle connections, and caps a user at 5
simultaneous streams.

**Rate limiting**: `RATE_LIMIT_REQUESTS` per authenticated user (or per IP when
anonymous) and `RATE_LIMIT_EMAILS_PER_RECIPIENT` per email address per hour.
Mandatory categories bypass the email limit. The counters live in Redis; if
Redis does not answer, the call is let through.

**Auditing**: every `*_UNRESTRICTED` access and every administrative action is
recorded in `notification_audit_log` with the actor, the subject and the
`X-Request-Id` that originated it.

**Traceability**: the `X-Request-Id` travels in the Kafka headers, enters the
consumers' MDC and is stored in `notification_dispatches.correlation_id`. With
that, `/api/v1/notifications/by-correlation/{id}` answers what was delivered on
each channel for one concrete request.

Swagger: `http://localhost:8082/swagger-ui.html`.

## Running locally

With the dockerized infrastructure up (see `api/docker/README.md`):

```bash
./mvnw spring-boot:run
```

The dev defaults point at `localhost` (Postgres 5432 / `sophia_notification`,
Redis 6379, Kafka 29092, MailHog 1025, the authentication JWKS at
`localhost:8080`). In Docker:
`docker compose -f docker-compose.notification.yml up -d --build` from
`api/docker` (port 8082).

The compose runs **a single instance** (`replicas: 1`, and the fixed
`container_name` prevents scaling it): it is the development environment, with
one Kafka broker and no load balancer. The service supports N replicas — the SSE
fan-out goes through Redis and the schedulers are guarded with ShedLock — but
horizontal scaling is done in Kubernetes (`k8s/`), not here.

## Testing

```bash
./mvnw test                 # unit tests, no Docker needed
./mvnw verify               # + integration tests (Testcontainers: real Postgres, Kafka, Redis and MailHog)
```

The E2E crosses the services: unlike the integration test, which exercises this
service against real infrastructure but simulates its producers, it registers a
user through the authentication API and verifies that the notification this
service never received directly ends up in the inbox and in the mailbox. It
needs the whole platform up:

```bash
cd api/docker && docker compose -f docker-compose.yml \
    -f docker-compose.authentication.yml \
    -f docker-compose.document-workspace.yml \
    -f docker-compose.notification.yml up -d --build
api/notification/e2e/notification-e2e.sh
```

Load (k6), with thresholds that fail the run so it doubles as a gate:

```bash
k6 run -e BASE_URL=http://localhost:8082 -e TOKEN=<jwt> load/inbox-load-test.js
```

The token needs `NOTIFICATION_READ` and `NOTIFICATION_UPDATE`.

Deployment: `k8s/` ships a Deployment with 3 replicas, PDB, HPA, NetworkPolicy,
probes and the Prometheus alert rules. CI in
`.github/workflows/notification-ci.yml`.

## Operations

- Actuator: `/actuator/health/{liveness,readiness}`, `/actuator/prometheus`.
  Both answer without authentication so the probes and the scrape work inside
  the cluster: **`/actuator/**` must not be routed through the public ingress**.
  That is the control, not authentication (see the NetworkPolicy in
  `k8s/config.yaml`).
- `readiness` includes the database on top of the lifecycle state: a pod that
  lost Postgres drops out of rotation. Redis is deliberately left out, because
  the inbox still serves without it and including it would let a downed Redis
  empty the load balancer.
- Key metrics: `notification_commands_total{result}`,
  `notification_in_app_created_total`, `notification_emails_queued_total`,
  `notification_emails_dispatched_total{result}`,
  `notification_user_events_total`, `notification_sse_connections`,
  `notification_messages_dead_lettered_total{source}` (worth alerting on that
  last one). The `category` tag only takes values from the catalogue: an unknown
  category counts as `unknown` instead of creating a new series.
- Alerts worth having: `notification_email_outbox_oldest_pending_age` climbing
  without pause means the email path stopped moving (which the send counters do
  not tell you), and any value of `notification_messages_dead_lettered_total` is
  a message nobody processed.
- Retention (configurable): read notifications 90d, all of them 365d, completed
  emails 7d, idempotency records 7d, audit 365d. The purge runs in batches of
  1000 rows, each in its own transaction.
- The email body is deleted from `email_outbox` as soon as it is delivered: that
  HTML contains the reset and verification token links, and it already lives in
  the recipient's mailbox. Failed ones keep it, because it is needed to retry.

## Configuration

| Variable | Default | What it controls |
| --- | --- | --- |
| `KAFKA_TOPIC_REPLICAS` | 1 (dev) / 3 (`prod`) | Replication of the topics this service creates |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | `SASL_SSL` against an authenticated cluster |
| `NOTIFICATION_COMMANDS_CONCURRENCY` | 3 | Consumers of the command topic (6 partitions) |
| `USER_EVENTS_CONCURRENCY` | 2 | Consumers of user-events |
| `REDIS_SSL` | `false` | TLS against Redis |
| `STREAM_HEARTBEAT_INTERVAL` | 20s | Lower than the proxy's idle timeout |
| `SCHEDULING_POOL_SIZE` | 4 | Outbox, heartbeat, gauges, maintenance |
| `SWAGGER_ENABLED` | `false` under `prod` | Swagger |

With `SASL_SSL` you additionally need `spring.kafka.properties.sasl.mechanism`
and `sasl.jaas.config` (passed as `SPRING_KAFKA_PROPERTIES_SASL_*`); they have no
default because an empty value breaks the client. TLS against Postgres goes in
`DB_URL` (`?sslmode=require`).
