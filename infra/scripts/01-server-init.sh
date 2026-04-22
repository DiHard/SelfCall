#!/usr/bin/env bash
# First-time VPS setup. Run as root on Ubuntu 22.04 / 24.04.
# Usage: bash 01-server-init.sh
set -euo pipefail

echo "==> Update system"
apt-get update -y
apt-get upgrade -y

echo "==> Install base packages"
apt-get install -y ca-certificates curl gnupg lsb-release ufw git

echo "==> Configure swap (1 GB) for Старт-3 tariff"
if [ ! -f /swapfile ]; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> Install Docker Engine + Compose plugin"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "==> Configure firewall (ufw)"
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp          comment 'SSH'
ufw allow 80/tcp          comment 'HTTP (ACME)'
ufw allow 443/tcp         comment 'HTTPS + WSS'
ufw allow 7881/tcp        comment 'LiveKit TCP fallback'
ufw allow 7882/udp        comment 'LiveKit UDP mux (WebRTC media)'
ufw allow 3478/udp        comment 'STUN/TURN'
ufw allow 3478/tcp        comment 'STUN/TURN TCP'
ufw allow 5349/tcp        comment 'TURN over TLS'
ufw allow 50000:50200/udp comment 'coturn relay range'
ufw --force enable
ufw status verbose

echo "==> Done. Reboot recommended if kernel was upgraded."
