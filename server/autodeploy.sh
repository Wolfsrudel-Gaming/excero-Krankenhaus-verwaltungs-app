#!/usr/bin/env bash
# Automatisches Server-Update: prüft das Git-Remote und deployt neue Commits.
# Wird von deploy.sh als systemd-Timer (alle 5 Minuten) eingerichtet.
set -euo pipefail
cd "$(dirname "$0")/.."

git fetch origin >/dev/null 2>&1
LOKAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse @{u})
if [ "$LOKAL" != "$REMOTE" ]; then
  echo "$(date '+%F %T') Neues Update gefunden ($REMOTE) – deploye …"
  git pull --ff-only
  cd server && docker compose up -d --build
  echo "$(date '+%F %T') Deployment abgeschlossen."
fi
