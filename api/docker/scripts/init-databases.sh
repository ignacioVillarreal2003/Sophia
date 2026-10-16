#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."
if [[ ! -f ./.env ]]; then
  echo "missing api/docker/.env - copy .env.example to .env first" >&2
  exit 1
fi
set -a; . ./.env; set +a

CONTAINER=sophia-postgres

create_db() {
  local db="$1"
  echo "==> ensuring database '$db'"
  docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$CONTAINER" \
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tc \
      "SELECT 1 FROM pg_database WHERE datname = '$db'" | grep -q 1 \
    || docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$CONTAINER" \
         psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
           -c "CREATE DATABASE $db OWNER $POSTGRES_USER"
}

create_db "$AUTHENTICATION_DB"
create_db "$DOCUMENT_WORKSPACE_DB"
create_db "$NOTIFICATION_DB"

echo "==> databases ready"
