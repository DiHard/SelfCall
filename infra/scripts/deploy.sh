#!/usr/bin/env bash
# Деплой с Mac на сервер: копирует код и пересобирает контейнеры SelfCall.
# Запуск из корня репозитория:  bash infra/scripts/deploy.sh
#
# Трогает только папку ~/selfcall на сервере и Docker-проект «selfcall».
# Файл infra/.env на сервере не перезаписывается и не удаляется.
# sudo на сервере спросит пароль.
set -euo pipefail

SERVER="${SERVER:-araratadmin@188.164.152.23}"
REMOTE_DIR="${REMOTE_DIR:-selfcall}"

cd "$(dirname "$0")/../.."

echo "==> Копирую код на $SERVER:~/$REMOTE_DIR"
rsync -az --delete \
  --exclude node_modules --exclude dist --exclude .venv \
  --exclude __pycache__ --exclude .pytest_cache --exclude '.env' \
  backend frontend infra "$SERVER:$REMOTE_DIR/"

echo "==> Пересобираю и перезапускаю контейнеры"
ssh -t "$SERVER" "cd $REMOTE_DIR/infra && test -f .env || { echo 'Нет infra/.env на сервере, см. infra/README.md'; exit 1; } && sudo docker compose up -d --build --remove-orphans && sudo docker compose ps"
