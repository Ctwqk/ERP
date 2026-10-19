#!/usr/bin/env bash
# Disposable stack: build images from verified jars, run all checks, always remove containers AND demo volumes.
# On shared hosts, run this entire script under the host's flock + systemd memory scope.
set -euo pipefail
cd "$(dirname "$0")/.."
for command in docker curl jq python3; do command -v "$command" >/dev/null; done
[[ -f .env ]] || { echo 'Copy .env.example to .env first.' >&2; exit 1; }
# Refuse to take over a developer's existing stack or data.
if [[ -n "$(docker ps -aq --filter label=com.docker.compose.project=pu-erp)" ]] ||
   [[ -n "$(docker volume ls -q --filter name=pu-erp-)" ]]; then
  echo 'An ERP stack or its data already exists. Use smoke-test.sh on that stack, or explicitly remove it first.' >&2
  exit 1
fi
cleanup() {
  result=$?
  trap - EXIT
  if (( result != 0 )); then docker compose logs --no-color --tail=100 >&2 || true; fi
  docker compose down --volumes --remove-orphans || result=1
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
export COMPOSE_PARALLEL_LIMIT=1
docker compose build
# Start JVMs one at a time to avoid simultaneous startup peaks.
docker compose up -d --wait --wait-timeout 180 postgres redis rabbitmq minio
for service in auth-service inventory-service item-service task-service notification-service; do
  docker compose up -d --wait --wait-timeout 180 "$service"
done
./scripts/smoke-test.sh
# Capture the configured memory limits and live use without dumping environment credentials.
docker stats --no-stream --format '{{.Name}}: {{.MemUsage}}' $(docker compose ps -q)
