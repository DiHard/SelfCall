#!/usr/bin/env bash
# Добавляет сайт SelfCall в nginx хоста. Запускать на сервере из папки infra:
#   sudo bash scripts/install-nginx-site.sh
#
# Что делает:
#   1. Пишет /etc/nginx/sites-available/selfcall.conf и ссылку в sites-enabled.
#   2. Проверяет конфиг (nginx -t). Если проверка не прошла — убирает свой файл,
#      nginx не перезагружается, другие сайты продолжают работать как раньше.
#   3. Перезагружает nginx (reload, без остановки).
# Сертификат выпускается отдельно: sudo certbot --nginx -d <домен>
set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck disable=SC1091
source .env

: "${DOMAIN:?задайте DOMAIN в .env}"
WEB_PORT="${WEB_PORT:-8110}"

AVAILABLE=/etc/nginx/sites-available/selfcall.conf
ENABLED=/etc/nginx/sites-enabled/selfcall.conf

sed -e "s/__DOMAIN__/${DOMAIN}/g" -e "s/__WEB_PORT__/${WEB_PORT}/g" \
  host-nginx/selfcall.conf.template > "$AVAILABLE"
ln -sf "$AVAILABLE" "$ENABLED"

if ! nginx -t; then
  echo "Конфиг nginx не прошёл проверку. Убираю selfcall.conf, nginx не перезагружаю." >&2
  rm -f "$ENABLED" "$AVAILABLE"
  exit 1
fi

systemctl reload nginx
echo "Готово: http://${DOMAIN} проксируется на 127.0.0.1:${WEB_PORT}."
echo "Дальше сертификат: sudo certbot --nginx -d ${DOMAIN}"
