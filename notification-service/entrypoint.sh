#!/bin/sh
set -e

PGHOST=${PGHOST:-postgres}
PGUSER=${PGUSER:-postgres}
PGPASSWORD=${PGPASSWORD:-postgres}
DB_NAME=${DB_NAME:-notificationdb}

export PGPASSWORD

psql -h "$PGHOST" -U "$PGUSER" -tc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 \
  || psql -h "$PGHOST" -U "$PGUSER" -c "CREATE DATABASE \"${DB_NAME}\""

exec "$@"

