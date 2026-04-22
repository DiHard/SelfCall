# SelfCall — Infrastructure

Self-hosted LiveKit stack (LiveKit SFU + coturn + Nginx + Let's Encrypt).

## Subdomains (NetAngels DNS → VPS IP)

| Subdomain | Purpose |
|-----------|---------|
| `app.if-x.ru` | React web app (static) |
| `api.if-x.ru` | Python FastAPI backend |
| `livekit.if-x.ru` | LiveKit signaling (WebSocket over TLS) |

Create **three A-records** in NetAngels DNS panel, all pointing to the VPS public IP.

## Open ports (firewall)

| Port | Proto | Purpose |
|------|-------|---------|
| 22   | tcp | SSH |
| 80   | tcp | HTTP (ACME challenge + redirect) |
| 443  | tcp | HTTPS + WSS |
| 7881 | tcp | LiveKit TCP fallback |
| 7882 | udp | LiveKit UDP mux (all WebRTC media) |
| 3478 | udp/tcp | STUN/TURN |
| 5349 | tcp | TURN over TLS |
| 50000–50200 | udp | coturn relay range |

## Deployment — step by step

### 1. Prepare the VPS

```bash
# SSH into the VPS as root
ssh root@<VPS_IP>

# Clone the repo
git clone https://github.com/DiHard/SelfCall.git
cd SelfCall/infra

# First-time server setup (Docker, firewall, swap)
bash scripts/01-server-init.sh
```

### 2. Configure secrets

```bash
cp .env.example .env

# Generate random keys and paste them into .env
bash scripts/gen-secrets.sh

# Edit domains / email / paste the generated secrets
nano .env
```

Also patch `coturn/turnserver.conf`: replace `REPLACE_ME_TURN_SECRET` with the
generated `TURN_SECRET` value and adjust `realm=` / domain if needed.

### 3. Issue TLS certificates (first run)

**Important:** before the first certbot run the Nginx configs that reference
cert files would fail. Temporarily move them aside:

```bash
mkdir -p nginx/conf.d.disabled
mv nginx/conf.d/app.conf nginx/conf.d/api.conf nginx/conf.d/livekit.conf nginx/conf.d.disabled/
```

Start Nginx (only the ACME server block is active):

```bash
docker compose up -d nginx
```

Issue certs:

```bash
bash scripts/02-issue-certs.sh
```

Re-enable the TLS configs:

```bash
mv nginx/conf.d.disabled/*.conf nginx/conf.d/
```

### 4. Start the full stack

```bash
docker compose up -d
docker compose ps
docker compose logs -f livekit
```

### 5. Smoke test

```bash
# LiveKit signaling endpoint should respond with "OK" over HTTPS:
curl -I https://livekit.if-x.ru

# coturn STUN binding test (from any machine):
# Use any online STUN tester with stun:livekit.if-x.ru:3478
```

## Directory layout

```
infra/
├── docker-compose.yml          # LiveKit + coturn + Nginx + certbot
├── .env.example                # Secrets / domain template
├── livekit/
│   └── livekit.yaml            # LiveKit server config
├── coturn/
│   └── turnserver.conf         # TURN/STUN config
├── nginx/
│   ├── nginx.conf              # Top-level
│   └── conf.d/
│       ├── 00-acme.conf        # ACME + HTTP->HTTPS redirect
│       ├── app.conf            # React static (app.if-x.ru)
│       ├── api.conf            # FastAPI proxy (api.if-x.ru)
│       └── livekit.conf        # LiveKit WSS proxy (livekit.if-x.ru)
└── scripts/
    ├── 01-server-init.sh       # Docker + firewall + swap
    ├── 02-issue-certs.sh       # Let's Encrypt
    └── gen-secrets.sh          # Random keys/secrets
```

## Notes

- LiveKit and coturn use **host networking** — they need direct UDP access,
  and Docker userland proxy adds latency/packet loss for media traffic.
- Nginx is bridged and maps only 80/443.
- `use_external_ip: true` in `livekit.yaml` makes LiveKit auto-detect the
  VPS public IP so WebRTC ICE candidates are correct.
- coturn shares the Let's Encrypt cert of `livekit.if-x.ru` for TURN-over-TLS.
