#!/usr/bin/env bash
# Generate random secrets for .env file.
# Usage: bash gen-secrets.sh   (prints to stdout; copy/paste into .env)
set -euo pipefail

echo "LIVEKIT_API_KEY=APIkey_$(openssl rand -hex 8)"
echo "LIVEKIT_API_SECRET=$(openssl rand -hex 32)"
echo "TURN_SECRET=$(openssl rand -hex 24)"
