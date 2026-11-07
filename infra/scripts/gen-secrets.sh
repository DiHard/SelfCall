#!/usr/bin/env bash
# Печатает случайные ключи для infra/.env (скопировать строки в файл).
# Запуск: bash scripts/gen-secrets.sh
set -euo pipefail

echo "LIVEKIT_API_KEY=API$(openssl rand -hex 8)"
echo "LIVEKIT_API_SECRET=$(openssl rand -hex 32)"
echo "MEETING_SECRET=$(openssl rand -hex 32)"
