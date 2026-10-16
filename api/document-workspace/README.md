# Sophia Document Workspace

Production-grade document management and realtime collaboration microservice —
the backend of a Notion/Confluence-class workspace: hierarchical artifacts,
block-based collaborative documents, Notion-style tables, mandatory access
control, full-text search, files on object storage, comments, templates,
versioning, import/export and a complete audit trail.

## Stack

Java 21 · Spring Boot 3.5 · PostgreSQL + Flyway · Redis · Kafka ·
OpenSearch · S3/MinIO · WebSocket/STOMP (Yjs-compatible CRDT sync) ·
MapStruct · springdoc-openapi · Micrometer/OTel · JUnit 5 + Testcontainers

## Architecture

Layered, mirroring the `authentication` service:

```
com.sophia.documentworkspace
├── api             controllers, filters, security adapters (JWT resource server, STOMP interceptor)
├── application     services, mappers, authorization (policy engine)
├── domain          entities, events, DTOs, exceptions, pure logic (block state machine, formula evaluator)
└── infrastructure  repositories, messaging (transactional outbox → Kafka), S3/OpenSearch/Redis gateways
```

Key design decisions:

- **Authentication is delegated** to the Sophia Authentication Service. This
  service validates HS256 JWTs (shared secret) and never stores credentials.
- **Access control combines four layers**, evaluated in order: workspace
  state/membership → **MAC (Bell-LaPadula)** over `PUBLIC…TOP_SECRET` labels
  (mandatory, never overridable) → RBAC role permissions → ownership → per-
  resource ACLs with folder inheritance. Content above a member's clearance is
  *invisible*, not merely forbidden.
- **Documents are block trees**, never blobs. Changes are operation batches
  applied by a deterministic state machine shared with revision replay, so
  live state and reconstructed history cannot diverge. Every batch is a
  revision; snapshots every 50 revisions bound replay cost.
- **Realtime** uses STOMP over WebSocket with per-frame authorization. Yjs
  updates are relayed and persisted (opaque) for late joiners; materialized
  operation batches go through the same revision pipeline as REST.
- **Domain events** are written to a transactional outbox and relayed to
  Kafka (`sophia.document-workspace.events`), keyed by aggregate id. In-process
  listeners drive search indexing, previews and audit. Notifications are
  composed here as commands and relayed through the same outbox to the
  notification service's topic (`sophia.notifications.commands`); that service
  owns the inbox, realtime stream and email delivery.
- **Files** live in S3/MinIO under immutable keys; PostgreSQL keeps metadata
  only. Uploads run size/quota checks, a virus-scan hook and SHA-256
  verification; large files use presigned multipart part URLs.

## Running locally

Every compose stack lives in [`api/docker`](../docker/README.md) (shared
infra, observability, one compose per API, all joined by the external
`sophia` network):

```bash
docker network create sophia                                        # once
cd ../docker
docker compose -f docker-compose.infrastructure.yml up -d                   # postgres, redis, kafka, opensearch, minio
docker compose -f docker-compose.authentication.yml up -d --build  # issues the JWTs
docker compose -f docker-compose.document-workspace.yml up -d --build
```

- API: http://localhost:8081 — Swagger UI at `/swagger-ui.html`
- MinIO console: http://localhost:9001 · Grafana (observability stack): http://localhost:3001

For development, run only the infra stack and start the API with Maven
(the dev profile defaults to localhost):

```bash
cd ../docker && docker compose -f docker-compose.infrastructure.yml up -d
cd ../document-workspace && ./mvnw spring-boot:run
```

## Testing

```bash
./mvnw test                   # unit tests
./mvnw verify                 # + integration tests (needs Docker) + 80% coverage gate
```

Integration tests boot the full application against Testcontainers-managed
PostgreSQL, Redis, Kafka, OpenSearch and MinIO, and exercise the REST API and
the STOMP channel end to end with real JWTs.

## Configuration

Everything is overridable via environment variables (see `.env.example`).
Secrets are never hardcoded; the prod profile fails fast when `JWT_SECRET` is
missing. Notable settings under the `sophia.*` prefix: JWT secret/issuer, CORS
origins, S3 bucket/endpoint/credentials, OpenSearch host, Kafka topic and
outbox relay cadence, invitation TTL, trash retention days, upload size limit.

## API surface (v1)

| Area | Base path |
| --- | --- |
| Workspaces / members / invitations | `/api/v1/workspaces`, `.../members`, `.../invitations` |
| Artifacts (hierarchy, move, duplicate, labels) | `/api/v1/workspaces/{ws}/artifacts` |
| Trash / favorites / recent / tags | `.../trash`, `.../favorites`, `.../recent`, `.../tags` |
| Document blocks / operations / revisions | `.../documents/{doc}/blocks|operations|revisions` |
| Realtime (CRDT log, presence, locks) | `.../documents/{doc}/realtime/*` + STOMP `/ws` |
| Tables (schema, rows, query) | `.../tables/{table}/*` |
| Files / multipart uploads | `.../files`, `.../uploads/multipart` |
| Search | `.../search?q=` |
| Comments | `.../artifacts/{a}/comments`, `.../comments/{c}` |
| Templates | `.../templates` |
| Export / import | `.../artifacts/{a}/export`, `.../import` |
| Permissions (ACL) | `.../artifacts/{a}/permissions` |
| Audit | `.../audit` |
```
