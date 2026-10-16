#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."
if [[ ! -f ./.env ]]; then
  echo "missing api/docker/.env - copy .env.example to .env first" >&2
  exit 1
fi
set -a; . ./.env; set +a

echo "==> ensuring buckets '$AUTHENTICATION_S3_BUCKET' and '$DOCUMENT_WORKSPACE_S3_BUCKET'"
docker run --rm --network sophia --entrypoint sh minio/mc -c "
  mc alias set local http://sophia-minio:9000 '$MINIO_ROOT_USER' '$MINIO_ROOT_PASSWORD' &&
  mc mb --ignore-existing local/'$AUTHENTICATION_S3_BUCKET' &&
  mc version enable local/'$AUTHENTICATION_S3_BUCKET' &&
  mc mb --ignore-existing local/'$DOCUMENT_WORKSPACE_S3_BUCKET' &&
  mc version enable local/'$DOCUMENT_WORKSPACE_S3_BUCKET'
"

echo "==> storage ready"
