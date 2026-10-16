# Sophia — Docker stacks

Todos los `docker-compose` del proyecto viven aquí, separados por responsabilidad
y unidos por una única red externa de Docker (`sophia`).

| Archivo | Contenido | Puertos host |
| --- | --- | --- |
| `docker-compose.infrastructure.yml` | PostgreSQL (una base por servicio), Redis, Kafka, OpenSearch, MinIO, MailHog | 5432, 6379, 29092, 9200, 9000/9001, 1025/8025 |
| `docker-compose.observability.yml` | Prometheus + Alertmanager + Tempo + Grafana (opcional) | 9090, 9093, 3200/4317/4318, 3001 |
| `docker-compose.authentication.yml` | API de autenticación | 8080 |
| `docker-compose.document-workspace.yml` | API de gestión documental (+ websocket `/ws`) | 8081 |
| `docker-compose.notification.yml` | API de notificaciones (in-app + email) | 8082 |

Cada API mantiene su `Dockerfile` en su propio directorio (`api/authentication`,
`api/document-workspace`, `api/notification`); estos composes solo los construyen.

> **Puertos:** este proyecto usa los mismos puertos de host que Lumen (y que Miga).
> Si querés correr varios a la vez, cambiá los puertos publicados de uno; los
> contenedores, la red (`sophia`) y los volúmenes ya están separados.

## Levantar todo

Desde `api/docker`, con Docker Desktop corriendo.

**bash / Git Bash / WSL:**

```bash
cp .env.example .env              # solo la primera vez
docker network create sophia      # solo la primera vez
docker compose -f docker-compose.infrastructure.yml up -d --wait
./scripts/init.sh                 # crea bases y buckets (idempotente)
docker compose -f docker-compose.authentication.yml up -d --build
docker compose -f docker-compose.document-workspace.yml up -d --build
docker compose -f docker-compose.notification.yml up -d --build
```

**PowerShell:** igual, pero los `.sh` se llaman con `bash ./scripts/init.sh`.

Observabilidad (opcional): `docker compose -f docker-compose.observability.yml up -d`.

El frontend Angular (`app/`) **no está dockerizado**:

```bash
cd ../../app
npm install   # solo la primera vez
npm start     # http://localhost:4200
```

El orden importa: `document-workspace` y `notification` validan los JWT contra el
JSON Web Key Set de `authentication`
(`http://sophia-authentication-api:8080/.well-known/json-web-key-set.json`).
`notification` consume el topic de comandos (`sophia.notifications.commands`, donde
publican `authentication` y `document-workspace`) y los eventos de usuario de
`authentication` (`sophia.authentication.user-events`).

## Configuración

El `.env` de este directorio no se versiona: se copia de `.env.example`. Contiene
solo lo compartido (infraestructura, nombres de bases y buckets, CORS, `JWT_ISSUER`,
observabilidad). Lo propio de cada servicio (Google OAuth, `JWT_PRIVATE_KEY`, etc.)
va en `api/<servicio>/.env` (ver sus `.env.example`).

## Bases y buckets

| Script | Qué hace |
| --- | --- |
| `scripts/init-databases.sh` | Crea una base por servicio en `sophia-postgres` |
| `scripts/init-storage.sh` | Crea los buckets de MinIO con versionado |
| `scripts/init.sh` | Corre los dos anteriores |

Bases: `sophia_authentication`, `sophia_document_workspace`, `sophia_notification`.

## URLs útiles

| Qué | URL |
| --- | --- |
| Authentication API / Swagger | http://localhost:8080/swagger-ui.html |
| Document API / Swagger | http://localhost:8081/swagger-ui.html |
| Notification API / Swagger | http://localhost:8082/swagger-ui.html |
| Grafana (admin/admin) | http://localhost:3001 |
| Prometheus | http://localhost:9090 |
| MailHog | http://localhost:8025 |
| MinIO console | http://localhost:9001 (minioadmin/minioadmin) |
| Kafka (desde el host) | `localhost:29092` |

## Estado, logs y limpieza

```bash
docker compose -f docker-compose.infrastructure.yml ps
docker logs -f sophia-document-workspace-api

docker compose -f docker-compose.notification.yml down
docker compose -f docker-compose.document-workspace.yml down
docker compose -f docker-compose.authentication.yml down
docker compose -f docker-compose.infrastructure.yml down      # añadir -v borra los datos
```

## Correr una API con Maven contra la infra dockerizada

```bash
docker compose -f docker-compose.infrastructure.yml up -d
./scripts/init.sh
cd ../document-workspace   # o ../authentication
./mvnw spring-boot:run
```

Dentro de la red `sophia` los hosts son los nombres de contenedor (`sophia-postgres`,
`sophia-redis`, `sophia-kafka:9092`, `sophia-opensearch`, `sophia-minio`); desde el
host son `localhost` con los puertos publicados (Kafka: `localhost:29092`).
