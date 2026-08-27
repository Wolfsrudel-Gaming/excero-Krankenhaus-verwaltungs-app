# KKH TV-Wartung – Server

Sync-Server + Weboberfläche für die Android-App. Läuft als Docker-Stack
(PostgreSQL + Node.js-Backend) parallel zu bestehenden Anwendungen auf dem
Rootserver, erreichbar unter dem Pfad `/kkh` der vorhandenen Domain.

## Installation (Ubuntu/Debian-Rootserver)

```bash
git clone <dieses-Repo> && cd <repo>/server
./deploy.sh
```

Das Skript installiert bei Bedarf Docker, erzeugt einmalig eine `.env` mit
zufälligen Zugangsdaten (API-Schlüssel für die App, Admin-Passwort für die
Weboberfläche) und startet den Stack. Der Dienst lauscht danach **nur lokal**
auf `127.0.0.1:8090` – öffentlich erreichbar wird er über den vorhandenen
Reverse-Proxy:

- **nginx:** Inhalt von `nginx-snippet.conf` in den `server {}`-Block der
  Domain einfügen, dann `nginx -t && nginx -s reload`
- **Apache:** `ProxyPass /kkh http://127.0.0.1:8090/kkh` (Module `proxy`,
  `proxy_http` aktivieren)

## Danach

- **Weboberfläche:** `https://DEINE-DOMAIN/kkh/` – Anmeldung mit
  `KKH_ADMIN_PASSWORD` aus `server/.env`
- **App verbinden:** In der App unter Einstellungen → Server-Synchronisation
  die Server-URL (`https://DEINE-DOMAIN/kkh`) und den `KKH_API_KEY` eintragen,
  „Automatisch synchronisieren" aktivieren, einmal „Jetzt synchronisieren".

## Betrieb

```bash
cd server
docker compose logs -f backend     # Logs
docker compose up -d --build       # Update nach git pull
docker compose down                # Stoppen (Daten bleiben in Volumes)
```

Daten liegen in den Docker-Volumes `kkh_pgdata` (Datenbank) und
`kkh_files` (Fotos/PDFs).

## Architektur / Ausbau

Das PostgreSQL-Schema (`backend/src/schema.sql`) ist die Grundlage für die
geplanten Ausbaustufen: Lagerlogistik (Artikel, Bestände, EK/VK-Preise,
Ein-/Ausbuchen) und Baustellenplanung folgen als weitere Tabellen und
Web-Module auf derselben Infrastruktur.
