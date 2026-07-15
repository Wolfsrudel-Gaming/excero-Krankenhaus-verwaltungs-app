#!/usr/bin/env bash
# KKH TV-Wartung – Server-Deployment (Ubuntu/Debian).
# Aufruf auf dem Rootserver:  cd server && ./deploy.sh
set -euo pipefail
cd "$(dirname "$0")"

echo "== KKH TV-Wartung – Server-Deployment =="

# Docker prüfen / installieren
if ! command -v docker >/dev/null 2>&1; then
  echo "Docker ist nicht installiert – installiere jetzt (get.docker.com) …"
  curl -fsSL https://get.docker.com | sh
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "Docker-Compose-Plugin fehlt – installiere …"
  apt-get update && apt-get install -y docker-compose-plugin
fi

# .env anlegen (Zugangsdaten werden einmalig generiert)
if [ ! -f .env ]; then
  echo "Erzeuge .env mit zufälligen Zugangsdaten …"
  API_KEY=$(head -c 24 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 32)
  ADMIN_PW=$(head -c 18 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 16)
  cat > .env <<EOF
# Zugangsdaten – gut aufbewahren!
KKH_API_KEY=${API_KEY}
KKH_ADMIN_PASSWORD=${ADMIN_PW}
KKH_SECRET=$(head -c 32 /dev/urandom | xxd -p -c 64)
PGPASSWORD=$(head -c 24 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 24)
BASE_PATH=/kkh
KKH_PORT=8090
EOF
  chmod 600 .env
fi

docker compose up -d --build

echo ""
echo "================================================================"
echo " Fertig! Der KKH-Server läuft lokal auf Port \${KKH_PORT:-8090}."
echo ""
echo " Zugangsdaten (auch in server/.env):"
grep -E 'KKH_API_KEY|KKH_ADMIN_PASSWORD' .env | sed 's/^/   /'
echo ""
echo " Nächster Schritt: Reverse-Proxy einrichten."
echo "   nginx:  Snippet aus nginx-snippet.conf in den server-Block"
echo "           der bestehenden Domain einfügen, dann: nginx -s reload"
echo "   Apache: ProxyPass /kkh http://127.0.0.1:8090/kkh"
echo ""
echo " Weboberfläche:   https://DEINE-DOMAIN/kkh/"
echo " App-Einstellung: Server-URL = https://DEINE-DOMAIN/kkh"
echo "                  API-Schlüssel = KKH_API_KEY (siehe oben)"
echo "================================================================"
