#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "$0")/.."
if [[ ! -f ./.env ]]; then
  echo "missing api/docker/.env - copy .env.example to .env first" >&2
  exit 1
fi
set -a; . ./.env; set +a

CONTAINER=nexo-postgres

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
create_db "$NOTIFICATION_DB"
create_db "$TOPIC_DB"
create_db "$SUBSCRIPTION_DB"
create_db "$CONTENT_DB"
create_db "$DELIVERY_DB"

# pgvector: la extensión se crea como superusuario en las bases que guardan embeddings.
for db in "$CONTENT_DB" "$DELIVERY_DB"; do
  docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" "$CONTAINER" \
    psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$db" -c "CREATE EXTENSION IF NOT EXISTS vector"
done

echo "==> databases ready"
