#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
if [[ ! -f .env ]]; then
  echo 'Нет infra/.env на сервере, см. infra/README.md' >&2
  exit 1
fi

builder=selfcall-build
container=buildx_buildkit_selfcall-build0
if ! docker buildx inspect "$builder" >/dev/null 2>&1; then
  docker buildx create --name "$builder" --node selfcall-build0 --driver docker-container \
    --driver-opt cpu-period=100000,cpu-quota=100000,memory=2g,memory-swap=2g,restart-policy=no
fi

docker buildx inspect --bootstrap "$builder"
limits=$(docker inspect --format '{{.HostConfig.CpuPeriod}} {{.HostConfig.CpuQuota}} {{.HostConfig.Memory}} {{.HostConfig.MemorySwap}}' "$container")
if [[ "$limits" != '100000 100000 2147483648 2147483648' ]]; then
  echo "У сборщика $builder неверные лимиты: $limits" >&2
  exit 1
fi

docker compose build --builder "$builder" backend web
docker compose up -d --no-build --remove-orphans
docker compose ps
