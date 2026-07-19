# KKH TV-Wartung

Android-App + Web-Verwaltungssystem für die Wartung der Fernseher im
Kinderkrankenhaus Köln (KKH), betrieben durch die **EXCERO GmbH**.

---

## Schnellstart

```bash
# Server starten
cd server && docker compose up -d

# Web-Panel öffnen
https://<server>/kkh/

# Standard-Login: Alexander / 123434 (bitte sofort ändern!)
```

---

## System-Architektur

```
┌──────────────────────┐     HTTPS/JSON     ┌──────────────────────────────┐
│  Android-App (Kotlin)│ ◄─────────────────► │  Node.js/Express + PostgreSQL│
│  Jetpack Compose     │    API-Schlüssel    │  Docker (Port 8090 intern)    │
│  Room-Datenbank      │                    │  Nginx Reverse-Proxy          │
└──────────────────────┘                    └──────────────────────────────┘
                                                          │
                                                  ┌───────┴────────┐
                                                  │  Web-Panel     │
                                                  │  /kkh/  (KKH)  │
                                                  │  /excero/ (Biz)│
                                                  └────────────────┘
```

**Zwei Web-Anwendungen auf demselben Server:**
- `/kkh/` – KKH TV-Wartungssystem (dieses Repo, dieser README)
- `/excero/` – Excero Geschäftsverwaltung (Rechnungen, Finanzen, Baustellen)

Beide teilen denselben Cookie-basierten Login (`kkh_session`).

---

## APK-Versionen

| Version | Code | Wichtigste Änderungen |
|---------|------|-----------------------|
| 1.9.5   | 20   | Auftragsnummern serverkoordiniert (keine Dubletten bei mehreren Geräten, Offline-Fallback); Nachbestell-Warnung in der Verwaltung aus dem Server-Lager |
| 1.9.4   | 19   | Datenschutz: Signaturen nicht mehr im Web-Panel sichtbar; Web-Panel vollständig überarbeitet (professionelles CSS, Mobile-Navigation, Bug-Fixes) |
| 1.9.3   | 18   | Deterministische Signatur-Uploads (`_signaturen/<station>_<zeitraumStart>_<rolle>.png`) für Server-seitige PDF-Generierung |
| 1.9.x   | –    | Team-Stundenzettel, Mehrbenutzer-Sync, Auto-Update, Freenet-Übersicht, Statistik, Berichtssuche |
| 1.8.x   | –    | Server-Synchronisation, Weboberfläche (erster Stand) |

---

## Funktionen der Android-App

### Zimmer & Stationen
- Alle 101 Stationen/Zimmer des KKH mit Stammdaten (TV-Typ, SN, Freenet-ID)
- Freenet-Ampel: 🔴 abgelaufen · 🟡 < 3 Monate · 🟢 OK
- Geprüfte Zimmer werden abgehakt; Team-Sicht zeigt wer geprüft hat
- Kein-Zutritt-Markierung je Zimmer (synchronisiert auf alle Geräte)
- Zimmer-Lebenslauf: automatischer Eintrag bei jeder Aktion

### Digitaler Prüfbogen
Prüfpunkte 1:1 zum Papierbogen:
- Empfang vorhanden
- Seriennummer stimmt (Abweichung → direkt als neue SN übernehmen)
- Freenet TV-ID stimmt (analog)
- DVD-Test
- Fernbedienung
- Halterung fest
- Gültigkeit Freenet > 3 Monate
- Freenet verlängert (→ neues Datum eintragen)
- Durchgeführte Arbeiten / Material (aus konfigurierbarer Liste)
- Freie Bemerkungen
- Eigene Zusatzpunkte (benutzerdefiniert)
- Fotos (fern + nah) via Systemkamera

### Stundenzettel
- Ein Zettel pro Station und Zeitraum (Auftragsnummer, Datum, Stunden, Anfahrt)
- Team-Einträge: eine Zeile pro Mitarbeiter (über Server synchronisiert, LWW)
- Einsatz-Zeiterfassung: „Einsatz starten/beenden" füllt eigene Zeile vor
- Unterschrift von Station + jedem Mitarbeiter (digital auf Gerät, für PDF)
- PDF-Export: lokal und über Server (inkl. Unterschriften)
- Unterschriften werden AUSSCHLIESSLICH für die PDF-Generierung verwendet
  und sind im Web-Panel nicht sichtbar (Datenschutz)

### Weitere Screens
| Screen | Funktion |
|--------|----------|
| Freenet-Ablauf | Alle ablaufenden Verträge sortiert nach Restlaufzeit |
| Statistik | Prüfungen/Monat/Station/Mitarbeiter, n.i.O.-Quoten, Verlängerungen |
| Berichtssuche & Papierkorb | Freitext-Suche; Soft-Delete + Wiederherstellung |
| Verwaltung | Material-Katalog, Prüfpunkt-Katalog, Echtstart-Reset |
| Export | ZIP (Fotos), XLSX (Übersicht + Protokolle), Server-Sync |
| Einstellungen | Server-URL, API-Key, Mitarbeiter-Name, Auto-Sync |

### Synchronisation mit dem Server
- **Zimmer:** Bidirektional (Last-Write-Wins über `updatedAt`)
- **Prüfbögen:** Push (App → Server), Delta-Pull (Kollegen-Berichte seit letztem Sync)
- **Stundenzettel:** Bidirektional LWW
- **Team-Einträge:** Bidirektional LWW
- **Mitarbeiterliste:** Pull (gepflegt im Web-Panel)
- **Fotos:** Push (nur eigene Fotos)
- **PDFs:** Push (lokal erstellte Stundenzettel-PDFs)
- **Unterschriften:** Push unter `_signaturen/<station>_<zeitraumStart>_<rolle>.png`
  (deterministisch → Server kann PDF ohne Geräte-ID regenerieren)
- **Auto-Update:** APK von Server herunterladen + installieren

---

## Funktionen des Web-Panels (`/kkh/`)

### Dashboard
- KPIs aus `/api/web/overview`: Zimmer gesamt/aktiv, Freenet abgelaufen/bald, Prüfungen 7/30 Tage
- Freenet-Warnungen mit direktem Link zu betroffenen Zimmern
- Klickbare KPI-Karten → Navigation zum jeweiligen View
- Letzte Prüfungen mit Prüfer-Name

### Zimmer & Stationen
- Tabelle mit Filter (Station, Status, Freenet), Suche, Export
- Farbliche Zeilen: `warn-row` (bald abgelaufener Freenet) · `crit-row` (abgelaufen) · `inaktiv-row`
- Detail-Ansicht: Stammdaten, Lebenslauf, aktive Sperren, Prüfhistorie mit Prüfer, Fotos
- Bearbeiten, Aktivieren/Deaktivieren

### Prüfungen
- Backend-Filter: von/bis, Station, Prüfer
- Detail-Dialog: Prüfpunkte (✅/❌), Arbeiten als Chips, Materialverbrauch, Anmerkungen
- Link zum Zimmer-Detail

### Stundenzettel
- Liste mit Datumsfilter, Direktlink zum PDF
- Detail: Kopfdaten bearbeiten, Team-Einträge (CRUD mit Vorbelegung), zugehörige Prüfungen
- Neu anlegen mit automatisch generierter Auftragsnummer (A-JJJJ-NNNN)

### Lager
- Artikel (Bestand-Warnungen, Buchen, Soft-Delete)
- Buchungen (Zeitraum-Filter, Typ-Filter)
- Verbrauch aus Prüfungen (Material-Match über Bezeichnung/App-Name)
- Nachbestellung (unter Mindestbestand, direkt buchen)
- Lieferanten (CRUD)
- Abrechnung & Auswertung

### Weitere Views
- Mitarbeiter (aktiv/inaktiv, anlegen)
- Dateien & Fotos (Galerie, PDFs, ZIP-Download)
- Benutzer (anlegen, Passwort-Reset, Umbenennen)

---

## Server-Deployment

```bash
# Erstes Deployment
cd server
./deploy.sh

# Update (läuft auch automatisch via systemd-Timer alle 5 Minuten)
git pull && cd server && docker compose build --no-cache && docker compose up -d
```

**Ports:**
- Backend intern: `127.0.0.1:8090`
- Nginx Reverse-Proxy: 80/443 (konfigurations-abhängig)

**Umgebungsvariablen** (`.env` im `server/`-Verzeichnis):
```
DB_PASSWORD=<sicheres Passwort>
API_KEY=<zufälliger Schlüssel, identisch in der App>
SESSION_SECRET=<zufälliger Schlüssel>
FILES_DIR=/data/files
```

---

## Datenschutz-Hinweise

| Datenart | App | Server (intern) | Web-Panel |
|----------|-----|-----------------|-----------|
| Zimmer-Stammdaten | ✅ | ✅ | ✅ |
| Prüfbögen | ✅ | ✅ | ✅ (lesen) |
| Fotos (Fernseher) | ✅ | ✅ | ✅ (Thumbnails) |
| Unterschriften | ✅ (erfassen) | ✅ (nur für PDF) | ❌ (gesperrt, HTTP 403) |
| Stundenzettel | ✅ | ✅ | ✅ |

Unterschriften werden über drei Ebenen geschützt:
1. `listFiles()` gibt `_signaturen`-Pfade nicht zurück
2. `/api/web/file` → HTTP 403 für `_signaturen`-Pfade
3. `/api/web/thumb` → HTTP 403 für `_signaturen`-Pfade

---

## Entwicklung

Siehe [ARCHITECTURE.md](./ARCHITECTURE.md) für:
- Vollständiger API-Vertrag (App ↔ Server)
- Datenbank-Schema
- Offene TODOs für die APK-Weiterentwicklung
- Code-Struktur-Übersicht

```bash
# APK bauen (braucht ANDROID_HOME gesetzt)
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
cd app
/opt/gradle/gradle-8.11.1/bin/gradle assembleDebug

# APK auf Server hochladen (nach Build)
cp app/build/outputs/apk/debug/app-debug.apk \
   server/backend/public/app/app-release.apk
```
