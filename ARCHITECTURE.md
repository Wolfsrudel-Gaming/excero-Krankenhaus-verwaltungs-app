# KKH TV-Wartung – Architektur & Entwicklungs-Referenz

> **Für Claude Code / KI-Assistent:** Diese Datei ist die primäre Referenz für die
> Weiterentwicklung der Android-App. Hier steht alles, was zum Weiterbauen gebraucht wird.

---

## 1. Verzeichnis-Struktur

```
excero-Krankenhaus-verwaltungs-app/
├── app/                          ← Android-App (Kotlin, Jetpack Compose)
│   ├── build.gradle.kts          ← versionCode=19, versionName="1.9.4"
│   ├── src/main/java/de/excero/tvwartung/
│   │   ├── App.kt                ← Application-Klasse, DI-Setup
│   │   ├── MainActivity.kt       ← NavHost, alle Composable-Routen
│   │   ├── data/
│   │   │   ├── Entities.kt       ← Room-Entities (alle Datenklassen)
│   │   │   ├── Daos.kt           ← Room-DAOs (alle Datenbankzugriffe)
│   │   │   ├── AppDatabase.kt    ← Room-DB v9, Migrations
│   │   │   ├── Repository.kt     ← Einziger Datenzugriffspunkt für ViewModels
│   │   │   ├── SettingsStore.kt  ← DataStore für App-Einstellungen
│   │   │   └── Arbeiten.kt       ← Enum/Konstanten für Prüfbogen-Arbeiten
│   │   ├── sync/
│   │   │   └── SyncManager.kt    ← GESAMTE Server-Kommunikation
│   │   ├── ui/
│   │   │   ├── AppViewModel.kt   ← EINZIGES ViewModel (Haupt-State)
│   │   │   ├── screens/          ← Alle Composable-Screens
│   │   │   └── theme/            ← Material3-Theme
│   │   ├── pdf/
│   │   │   ├── PruefberichtPdf.kt   ← Lokale PDF-Generierung (PdfDocument)
│   │   │   └── StundenzettelPdf.kt  ← Lokale Stundenzettel-PDF
│   │   └── files/
│   │       ├── PhotoStore.kt     ← Foto-Verwaltung (interne App-Dirs)
│   │       ├── SignatureStore.kt  ← Unterschriften-Verwaltung (intern)
│   │       ├── ZipExporter.kt    ← ZIP-Export für Fotos
│   │       └── BackupManager.kt  ← Lokales SQLite-Backup
│   └── keystore/debug.keystore   ← Fester Signing-Key (im Repo, kein Geheimnis)
│
├── server/                       ← Server-Komponenten
│   ├── backend/
│   │   ├── src/
│   │   │   ├── index.js          ← Haupt-Express-Server (~1550 Zeilen)
│   │   │   ├── schema.sql        ← PostgreSQL-Schema + Seed-Daten
│   │   │   └── pdf/
│   │   │       ├── stundenzettel.js ← Server-seitige PDF-Generierung
│   │   │       └── rechnung.js      ← Rechnungs-PDF (für Excero-System)
│   │   ├── public/               ← KKH Web-Panel Statik-Assets
│   │   │   ├── index.html
│   │   │   ├── app.js            ← SPA-Shell, Router, Auth
│   │   │   ├── ui.js             ← Shared UI-Components (modal, DataGrid, etc.)
│   │   │   ├── style.css         ← Vollständiges CSS-System
│   │   │   └── views/            ← Einzelne View-Module
│   │   │       ├── dashboard.js
│   │   │       ├── zimmer.js
│   │   │       ├── pruefungen.js
│   │   │       ├── stundenzettel.js
│   │   │       ├── lager.js      ← Enthält auch viewAbrechnung + viewMitarbeiter
│   │   │       ├── benutzer.js
│   │   │       ├── dateien.js
│   │   │       └── ki.js         ← KI-Prüfung (Abweichungen bestätigen)
│   │   └── public-excero/        ← Excero Web-App (separates System)
│   ├── ki/                       ← KI-Fotoerkennung (Python/FastAPI, eigener Container)
│   │   ├── main.py               ← FastAPI: /status, /analyse, /train
│   │   ├── pipeline.py           ← Foto → Klassifikation → OCR → Felder → Abgleich
│   │   ├── worker.py             ← Warteschlangen-Worker + nächtliches Training (3:30)
│   │   ├── training.py           ← Training beider Netze aus ki_labels
│   │   ├── db.py                 ← PostgreSQL-Zugriff (gleiche DB wie Backend)
│   │   ├── models/klassifikator.py ← Eigenes CNN (Bildtyp + TV-Typ)
│   │   ├── models/feldnetz.py    ← Eigenes MLP (OCR-Zeile → Feldtyp) + Format-Regeln
│   │   └── README.md             ← Vollständige KI-Doku (Pipeline, Training, Betrieb)
│   ├── docker-compose.yml        ← Services: db, backend, ki
│   └── deploy.sh
│
├── README.md                     ← Benutzer-Dokumentation
└── ARCHITECTURE.md               ← Diese Datei (Entwickler-Referenz)
```

---

## 2. Datenbank-Schema (Room, v9)

### Tabellen

```kotlin
// tv_rooms: Zimmerstammdaten (bidirektional sync)
TvRoom(
    id: String,           // PK: "<Station>_<Zimmer>" z.B. "A4_01a"
    station, zimmer, lebenslauf, letztePruefung,
    tvTyp, seriennummer, freenetId, gueltigBis,
    inaktiv: Boolean,     // Soft-Delete
    updatedAt: String     // ISO-DateTime für LWW-Sync
)

// inspections: Prüfbögen (Push-only von App)
Inspection(
    id: Long (autoGen),
    roomId: String,       // FK zu tv_rooms.id
    datum: String,        // ISO-Datum
    empfangVorhanden, seriennummerStimmt, freenetIdStimmt,
    dvdTest, fernbedienung, halterungFest,
    gueltigkeitAusreichend, freenetVerlaengert: Boolean?,
    bemerkung*: String,   // Je Prüfpunkt
    bemerkungen: String,  // Freitext
    arbeiten: String,     // Zeilengetrennte Liste
    extraPunkte: String,  // JSON: [{"t":Titel,"e":bool|null,"b":Bemerkung}]
    uuid: String,         // Eindeutig geräteübergreifend (sync-ID)
    mitarbeiter: String,  // Wer hat geprüft
    geloescht: Boolean    // Papierkorb
)

// stundenzettel: Stunden-Nachweise (bidirektional LWW)
StundenzettelEntity(
    id: Long (autoGen),
    station, zeitraumStart,  // Composite-Key für sync
    auftragsnummer, datum, stunden, anfahrt, techniker,
    updatedAt: String
)

// stundenzettel_eintraege: Team-Zeilen (bidirektional LWW)
StundenzettelEintrag(
    station, zeitraumStart, mitarbeiter,  // Composite-PK
    stunden, anfahrt,
    updatedAt: String
)

// room_sperren: Kein-Zutritt-Markierungen
RoomSperre(
    roomId: String (PK),
    gesperrtAm: String,
    grund: String
)

// materialien: App-seitiger Material-Katalog
Material(
    id: Long (autoGen),
    name, bestand, bestandAktiv, aktiv, sortIndex
)

// custom_pruefpunkte: Benutzerdefinierte Prüfpunkte
CustomPruefpunkt(id, titel, aktiv, sortIndex)

// activity_log: Interne Aktivitäts-Historie (wird NICHT exportiert)
ActivityLog(id, roomId, zeitpunkt, aktion)

// einsaetze: Zeiterfassung "Einsatz Start/Ende"
Einsatz(id, station, mitarbeiter, start, ende)
```

### DB-Migration
Aktuelle Version: **9** (in `AppDatabase.kt`)
Bei Schemaänderungen: Migration in `AppDatabase.kt` hinzufügen!
```kotlin
// Beispiel Migration v9→v10:
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inspections ADD COLUMN neuesSpalte TEXT DEFAULT '' NOT NULL")
    }
}
// Dann in Room.databaseBuilder(...).addMigrations(MIGRATION_9_10)
```

---

## 3. Server-API-Vertrag

### Basis-URL
`<SERVER_URL>` aus den App-Einstellungen, z.B. `https://excero.de/kkh`

### Authentifizierung
Alle Sync-Endpunkte: `X-Api-Key: <API_KEY>` Header  
Web-Endpunkte: Cookie `kkh_session`

---

### 3.1 App → Server: Sync-Endpunkte

#### `GET /api/sync/rooms`
Alle Zimmer vom Server.
```json
{ "rooms": [{ "id", "station", "zimmer", "lebenslauf", "letztePruefung",
              "tvTyp", "seriennummer", "freenetId", "gueltigBis",
              "inaktiv", "updatedAt" }] }
```

#### `POST /api/sync/rooms`
Zimmer hochladen (LWW).
```json
{ "rooms": [{...gleiche Felder...}] }
// Antwort:
{ "uebernommen": 5 }
```

#### `POST /api/sync/inspections`
Prüfbögen pushen (Server dedupliziert über UUID).
```json
{ "inspections": [{
    "uuid", "roomId", "datum",
    "punkte": [{"titel", "ergebnis": true|false|null, "bemerkung"}],
    "arbeiten": ["Material X", "Freenet verlängert"],
    "bemerkungen",
    "mitarbeiter",   // ← WICHTIG für Prüfer-Spalte im Web
    "geloescht"
}] }
// Antwort: { "neu": 3 }
```

#### `GET /api/sync/inspections?since=<ISO-DateTime>`
Delta-Pull: Prüfbögen der Kollegen seit `since`.
```json
{ "inspections": [{...gleiche Struktur...}] }
```

#### `POST /api/sync/stundenzettel`
```json
{ "zettel": [{ "station", "zeitraumStart", "auftragsnummer",
               "datum", "stunden", "anfahrt", "techniker", "updatedAt" }] }
```

#### `GET /api/sync/stundenzettel`
Server-Stand für Pull.

#### `POST /api/sync/zettel-eintraege` + `GET /api/sync/zettel-eintraege`
Team-Zeilen (LWW bidirektional).
```json
{ "eintraege": [{ "station", "zeitraumStart", "mitarbeiter",
                  "stunden", "anfahrt", "updatedAt" }] }
```

#### `GET /api/sync/mitarbeiter`
Mitarbeiterliste für Gerät-Konfiguration.
```json
{ "mitarbeiter": [{ "name", "aktiv": true }] }
```

#### `GET /api/sync/files`
Vorhandene Dateien mit Pfad + Größe (für Upload-Deduplizierung).
```json
{ "files": [{ "path": "KKH-A4_01/20260714_fern.jpg", "size": 124500 }] }
```

#### `PUT /api/sync/file?path=<url-encoded-pfad>`
Datei hochladen (binary, Content-Type: application/octet-stream).
- Fotos: `<roomId>/<YYYYMMDD>_fern.jpg` etc.
- PDFs: `_stundenzettel/<station>_<zeitraumStart>.pdf`
- Signaturen: `_signaturen/<station>_<zeitraumStart>_<rolle>.png`
  (Rollen: `station`, `<mitarbeiter-name>`)

> ⚠️ Signaturen werden hochgeladen für Server-seitige PDF-Generierung,
> sind aber über `/api/web/*` NICHT abrufbar (HTTP 403).

#### `POST /api/sync/sperren`
```json
{ "sperren": [{ "roomId", "gesperrtAm", "grund" }] }
```

#### `POST /api/sync/material`
```json
{ "material": [{ "name", "bestand", "bestandAktiv", "aktiv", "sortIndex" }] }
```

#### `POST /api/sync/pruefpunkte`
```json
{ "punkte": [{ "titel", "aktiv", "sortIndex" }] }
```

#### `POST /api/sync/aktivitaet`
```json
{ "eintraege": [{ "roomId", "zeitpunkt", "aktion" }] }
```

---

### 3.2 App-Update-Endpunkte

#### `GET /app/version.json`
```json
{ "versionCode": 19, "versionName": "1.9.4" }
```

#### `GET /app/app-release.apk`
Aktuelle APK herunterladen.

---

### 3.3 Web-Only-Endpunkte (braucht Cookie-Session)

Diese Endpunkte sind für das Web-Panel und werden von der App **nicht** genutzt:

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| POST | `/api/login` | Web-Login |
| GET | `/api/web/overview` | Dashboard-Aggregate |
| GET | `/api/web/rooms` | Zimmer-Liste |
| GET/PATCH | `/api/web/rooms/:id` | Zimmer-Detail/Bearbeiten |
| GET | `/api/web/inspections` | Prüfungen (mit Filtern) |
| GET/PUT/DELETE | `/api/web/stundenzettel` | Stundenzettel-CRUD |
| GET | `/api/web/stundenzettel/next-nr` | Nächste Auftragsnummer |
| GET | `/api/web/stundenzettel/pdf` | Stundenzettel als PDF |
| GET/POST/PATCH/DELETE | `/api/web/users` | Benutzerverwaltung |
| GET | `/api/web/lager/*` | Lagerverwaltung |
| GET | `/api/web/ki/analysen` | KI-Foto-Analysen (Filter: `status`, `room`) |
| POST | `/api/web/ki/analysen/:id/bestaetigen` | Entscheidung speichern (wird Trainingslabel) |
| POST | `/api/web/ki/analysen/:id/neu` | Foto erneut analysieren |
| GET | `/api/web/ki/status` | KI-Warteschlange + Modell-Versionen |
| POST | `/api/web/ki/train` | Training sofort anstoßen |

---

### 3.4 KI-Fotoerkennung (Service `server/ki/`)

Eigener Docker-Container (`ki`, intern `http://ki:8100`), analysiert alle
Prüfungsfotos automatisch:

1. **Bildklassifikation** (eigenes CNN): CI-Menü / Gerät / Übersichtsfoto + TV-Typ
2. **OCR** (EasyOCR, de+en, CPU): liest den Bildschirmtext des CI-Menüs
3. **Feld-Zuordnung** (eigenes MLP + Format-Regeln): Seriennummer, Freenet-ID, Gültig-bis
4. **Abgleich** mit den Zimmer-Stammdaten → Status `uebereinstimmung` / `abweichung` / `unlesbar`

Ablauf: Foto-Upload (`PUT /api/sync/file`) → Insert in `foto_analysen`
(Status `wartet`) → KI-Worker analysiert → Ergebnis im Web-Panel unter
**KI-Prüfung**. Jede Bestätigung im Web wird Trainingslabel (`ki_labels`);
Training läuft nächtlich um 3:30 Uhr automatisch. Details: `server/ki/README.md`.

**Datenschutz:** `_signaturen`-Pfade werden niemals analysiert oder eingereiht.

Neue Tabellen: `foto_analysen`, `ki_labels`, `ki_modelle` (siehe `schema.sql`).

---

## 4. App-Screens & Navigation

Navigations-Graph in `MainActivity.kt`:

```
HomeScreen
├── RoomDetailScreen(roomId)
│   ├── PruefbogenScreen(roomId) → zurück zu Detail
│   └── RoomEditScreen(roomId) → zurück zu Detail
├── StundenzettelListeScreen(station?) 
│   └── StundenzettelScreen(station, zeitraumStart)
├── ExportScreen
├── SettingsScreen
├── VerwaltungScreen (Material + Prüfpunkte)
├── FreenetScreen
├── StatistikScreen
└── SucheScreen (Berichte & Papierkorb)
```

### State-Verwaltung
`AppViewModel` ist das zentrale ViewModel mit StateFlows für:
- `rooms`: Alle Zimmer
- `inspectionsInPeriod`: Prüfungen im eingestellten Zeitraum
- `settings`: SettingsStore-Daten
- `gesperrteZimmer`: Aktive Sperren
- `updateVerfuegbar`: (versionCode, versionName) wenn Update vorhanden
- `syncErgebnis`: Letztes Sync-Ergebnis

---

## 5. Einstellungen (SettingsStore)

Gespeichert als Protocol-Buffer Datastore (`settings.pb`):

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| serverUrl | String | Basis-URL, z.B. `https://excero.de/kkh` |
| apiKey | String | X-Api-Key für Sync |
| mitarbeiter | String | Name des Technikers auf diesem Gerät |
| bekannteMitarbeiter | List\<String\> | Vom Server gezogene Mitarbeiter-Liste |
| prüfzeitraumStart | String | ISO-Datum, Beginn des aktuellen Zeitraums |
| prüfzeitraumEnde | String | ISO-Datum, Ende des aktuellen Zeitraums |
| autoSync | Boolean | Sync beim App-Start |
| syncNachPruefung | Boolean | Sync nach jedem Prüfbogen |
| lastSync | String | ISO-DateTime des letzten erfolgreichen Syncs |

---

## 6. Bekannte TODOs / Offene Punkte für die APK

### 🔴 Dringend / Bug

1. **Einsatz-Zeiterfassung → Stundenzettel-Befüllung prüfen**
   - `Einsatz`-Entity existiert, aber der Flow von "Einsatz beenden" → 
     StundenzettelEintrag-Vorbelegung muss im `AppViewModel` geprüft werden
   - Datei: `AppViewModel.kt`, Methoden `startEinsatz()`, `endeEinsatz()`

2. **DB-Migration für neue Spalten prüfen**
   - Falls in Zukunft neue Spalten zu `inspections` kommen, Migration nicht vergessen
   - Aktuelle Version: 9 (in `AppDatabase.kt`)

### 🟡 Feature-Verbesserungen

3. **Server-Lagerbestand in der App anzeigen**
   - Problem: App hat eigenen Material-Katalog (offline-first), Server hat ein
     vollständiges Lagersystem mit Buchungen und Mindestbeständen
   - Idee: Neuer Sync-Endpunkt `GET /api/sync/lager-status` der dem Gerät
     sagt, welche Artikel unter Mindestbestand sind → Warnung in VerwaltungScreen
   - Benötigt: Neuer Endpoint im Backend, neuer Flow in SyncManager

4. **Offline-Modus verbessern**
   - Wenn Sync fehlschlägt, wird die Fehlermeldung angezeigt aber die App
     funktioniert ohne Server komplett. Das ist gut, aber:
   - TODO: Sync-Queue für Prüfbögen die offline erfasst wurden und beim
     nächsten Sync automatisch hochgeladen werden (aktuell immer alle)

5. **Foto-Verwaltung verbessern**
   - PhotoSection zeigt Fotos pro Zimmer; es gibt aber kein Löschen von Fotos
   - Fehlende Funktion: Foto löschen (lokal + ggf. Server-Hinweis)
   - Datei: `PhotoSection.kt`, `PhotoStore.kt`

6. **Stundenzettel-Auftragsnummer vom Server vorschlagen**
   - Server hat `/api/web/stundenzettel/next-nr` → `{ auftragsnummer, nr }`
   - Die App vergibt Auftragsnummern aktuell lokal (nicht koordiniert mit Server)
   - Idee: Beim Anlegen eines neuen Stundenzettels Server nach next-nr fragen
   - Benötigt: Neuer API-Call in `SyncManager` oder direkt im ViewModel

7. **Berichtssuche: Filter nach Mitarbeiter**
   - `SucheScreen.kt` hat Freitext-Suche, aber kein Mitarbeiter-Filter-Dropdown
   - Mitarbeiterliste ist in `settings.bekannteMitarbeiter` verfügbar

8. **Echtstart-Reset verbessern**
   - `VerwaltungScreen.kt` → "Echtstart vorbereiten" löscht Testdaten
   - Besser: Nur Daten löschen, die nach einem konfigurierbaren Datum liegen

9. **KI-Unterstützung in der App (Server-KI ist fertig)**
   - Der Server analysiert alle Fotos automatisch (siehe Abschnitt 3.4)
   - a) **Dritter Foto-Button „CI-Menü"** in `PhotoSection.kt`: Dateiname mit
     `_menue_` statt `_nah_` → die Server-KI weiß dann sicher, welches Foto
     das CI-Menü zeigt (aktuell klassifiziert sie selbst)
   - b) **KI-Abweichungen beim Sync anzeigen**: `GET /api/web/ki/analysen?status=abweichung`
     existiert; ein App-tauglicher Sync-Endpunkt (X-Api-Key) müsste im Backend
     ergänzt werden (ca. 10 Zeilen analog zu den anderen Sync-Endpunkten).
     Der Monteur sieht dann direkt am Gerät: „KI hat auf deinem Foto SN X
     gelesen, in den Stammdaten steht Y – bitte prüfen."

### 🟢 Nice-to-Have

10. **Widgets / Schnellzugriff**
    - Home-Screen-Widget: "X von Y geprüft heute" mit Tippen → App öffnen

11. **Prüfbogen-Vorlagen**
    - Oft werden dieselben Arbeiten gemacht; Vorlage speichern und laden

12. **NFC-Tag für Zimmer**
    - NFC-Chip am TV → Zimmer direkt öffnen statt durch Liste scrollen
    - `TvRoom.id` als NFC-NDEF-Inhalt schreiben/lesen

13. **Statistik erweitern**
    - Durchschnittliche Prüfzeit pro Zimmer (via `activity_log`)
    - Vergleich Zeiträume (aktuell vs. letzter Zeitraum)

---

## 7. APK Bauen

### Voraussetzungen
```bash
# Android SDK (min. API 26, target 35)
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk

# Gradle (8.11.1 empfohlen)
/opt/gradle/gradle-8.11.1/bin/gradle --version
```

### Build-Kommando
```bash
cd /root/excero-Krankenhaus-verwaltungs-app/app
/opt/gradle/gradle-8.11.1/bin/gradle assembleDebug

# APK-Pfad:
# app/build/outputs/apk/debug/app-debug.apk
```

### Nach dem Build
```bash
# 1. APK auf Server kopieren
cp app/build/outputs/apk/debug/app-debug.apk \
   ../server/backend/public/app/app-release.apk

# 2. version.json aktualisieren (server/backend/public/app/version.json)
# { "versionCode": 19, "versionName": "1.9.4" }

# 3. Docker neu bauen und starten
cd ../server && docker compose build --no-cache && docker compose up -d

# 4. Commit + Push
git add -A && git commit -m "APK v1.9.X: ..."
git push
```

---

## 8. Server-Backend: Wichtige Code-Stellen

### `server/backend/src/index.js`

| Zeile (ca.) | Was passiert dort |
|-------------|------------------|
| 1–100 | Imports, DB-Pool, Hilfsfunktionen (hashPassword, signSession, etc.) |
| 100–160 | `listFiles()`, `roomToJson()`, `upsertRoomLww()` |
| 160–270 | Auth-Middleware (`requireWebAuth`, `checkSession`) |
| 270–400 | Web-Benutzer-CRUD |
| 400–430 | `GET /api/web/overview` (Dashboard-Aggregate) |
| 430–560 | Web-Zimmer-API |
| 560–660 | Web-Stundenzettel-API (inkl. next-nr, PDF) |
| 660–830 | Web-Lager-API (Artikel, Buchungen, Verbrauch, Nachbestellung, Lieferanten) |
| 830–1050 | Web-Mitarbeiter + sonstige Web-Endpunkte |
| 1050–1200 | **Sync-Endpunkte** (App → Server) |
| 1200–1350 | Datei-Upload (`PUT /api/sync/file`) + Web-File-Serving |
| 1350–1430 | Export-Endpunkte (XLSX, ZIP, PDF) |
| 1430–1450 | `/api/web/thumb` (Thumbnail-Generierung mit sharp) |
| 1450–1520 | Auto-Backup (pg_dump, nächtlich) |

### `server/backend/src/schema.sql`

Das Schema wird beim Start der App über `init()` angewendet.
`CREATE TABLE IF NOT EXISTS` → idempotent, sicher für Wiederholungen.
Seeding: `INSERT INTO ... WHERE NOT EXISTS` → nur beim ersten Mal.

---

## 9. Datenschutz-Regeln (fest eingebaut)

```
REGEL: Unterschriften sind NIEMALS über die Web-API abrufbar.

Implementierung:
1. listFiles() in index.js → filtert _signaturen-Pfade heraus
2. GET /api/web/file → HTTP 403 wenn path.includes('_signaturen')
3. GET /api/web/thumb → HTTP 403 wenn path.includes('_signaturen')
4. dateien.js (Frontend) → filtert zusätzlich _signaturen clientseitig
5. zimmer.js (Frontend) → filtert Signaturen aus der Foto-Galerie

ERLAUBT:
- App lädt Signaturen hoch (PUT /api/sync/file mit _signaturen-Pfad)
- Server liest Signaturen für PDF-Generierung direkt vom Filesystem
  (stundenzettel.js → fs.existsSync + fs.readFileSync)
```

---

## 10. Web-Panel: UI-System

### CSS-Variablen (style.css)
```css
--teal: #00695C;     /* Primärfarbe KKH */
--teal-d: #004d43;   /* Dunkel für Sidebar/Header */
--ok: #2E7D32;       --ok-l: #E8F5E9;
--warn: #E65100;     --warn-l: #FFF3E0;
--err: #C62828;      --err-l: #FFEBEE;
--info: #1565C0;     --info-l: #E3F2FD;
```

### JavaScript-Utilities (ui.js)
```javascript
// API-Aufruf (Base /kkh automatisch in app.js gesetzt)
api('/kkh/api/web/rooms')

// Modal mit Formular – WICHTIG: Werte über mf() lesen, NICHT über getElementById!
const res = await modal('Titel', `<input id="m-name">`,
    [{ label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
if (!res || res.action !== 'ok') return;
const name = mf(res, 'm-name'); // ← So, nicht getElementById nach Modal!

// DataGrid
new DataGrid(containerElement, {
    data: rows,          // Array von Objekten
    filterKeys: ['name', 'id'],  // Felder für Textsuche
    columns: [
        { key: 'name', label: 'Name', sort: true },
        { key: 'status', label: 'Status',
          render: (val, row) => badge(val, 'ok') },  // Custom-Render
    ],
    onRowClick: (row) => viewDetail(row.id),
});

// Badges
badge('Text', 'ok'|'warn'|'err'|'info'|'gray'|'teal')
freenetBadge(gueltigBis)  // Automatische Farbe nach Datum

// Toast
toast('Gespeichert')         // Grün
toast('Fehler!', 'err')      // Rot
toast('Achtung', 'warn')     // Orange
```
