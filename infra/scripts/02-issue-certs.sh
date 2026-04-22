#!/usr/bin/env bash
# Obtain Let's Encrypt certificates for all three subdomains.
# Run AFTER:
#   - DNS A-records for app.if-x.ru / api.if-x.ru / livekit.if-x.ru point to this VPS
#   - Nginx is running with 00-acme.conf enabled (HTTP-only, webroot /var/www/certbot)
#
# Temporarily DISABLE the *.conf files that reference certs before first run.
set -euo pipefail

cd "$(dirname "$0")/.."

# shellcheck disable=SC1091
source .env

EMAIL="${ACME_EMAIL:?set ACME_EMAIL in .env}"

for DOMAIN in "$APP_DOMAIN" "$API_DOMAIN" "$LIVEKIT_DOMAIN"; do
  echo "==> Issuing cert for $DOMAIN"
  docker compose run --rm --entrypoint "" certbot \
    certbot certonly --webroot -w /var/www/certbot \
    -d "$DOMAIN" --email "$EMAIL" --agree-tos --no-eff-email --non-interactive
done

echo "==> Certs issued. Reload nginx:"
echo "    docker compose exec nginx nginx -s reload"
