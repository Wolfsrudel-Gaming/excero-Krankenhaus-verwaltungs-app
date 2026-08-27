# Sync-API-Vertrag: Android-App ⇄ Server

**Diese Schnittstelle ist der Vertrag zwischen der KKH-Android-App**
(Repo `excero-Krankenhaus-verwaltungs-app`) **und dem Server.**
Die Weboberfläche und alles unter `/api/web/*` darf frei geändert werden —
die folgenden `/api/sync/*`-Endpunkte dürfen nur **additiv** verändert werden
(neue optionale Felder ja; umbenennen/entfernen/Pflichtfelder nein), sonst
brechen die Apps im Feld.

Auth: Header `X-Api-Key: <KKH_API_KEY aus .env>` bei allen Sync-Endpunkten.
Datumsformate: Datum `YYYY-MM-DD`, Zeitstempel `YYYY-MM-DDTHH:MM:SS` (lokal).
Konfliktlösung: last-write-wins über `updatedAt` (String-Vergleich).

## GET /api/sync/rooms
Antwort: `{ "rooms": [Room] }`
```
Room = { id, station, zimmer, lebenslauf, letztePruefung, tvTyp,
         seriennummer, freenetId, gueltigBis, inaktiv:bool, updatedAt }
```
Die App übernimmt Server-Zeilen, deren `updatedAt` neuer ist als ihr lokaler
Stand (so kommen Web-Änderungen aufs Handy).

## POST /api/sync/rooms
Body: `{ "rooms": [Room] }` (App schickt IMMER alle Zimmer).
Server übernimmt pro Zeile nur, wenn `updatedAt` neuer ist (LWW-Upsert).
Antwort: `{ "uebernommen": n }`

## POST /api/sync/inspections
Body: `{ "inspections": [ { uuid, roomId, datum,
  punkte: [ { titel, ergebnis: true|false|null, bemerkung } ],
  arbeiten: [string], bemerkungen } ] }`
App schickt immer alle; Server dedupliziert über `uuid`
(INSERT … ON CONFLICT DO NOTHING). Antwort: `{ "neu": n }`

## POST /api/sync/stundenzettel
Body: `{ "zettel": [ { station, zeitraumStart, auftragsnummer, datum,
  stunden, anfahrt, techniker, updatedAt } ] }`
Upsert über (station, zeitraumStart) mit LWW. Antwort: `{ "uebernommen": n }`

## GET /api/sync/stundenzettel
Pull aller Zettel-Header (inkl. Web-Edits). Antwort:
`{ "zettel": [ { station, zeitraumStart, auftragsnummer, datum,
  stunden, anfahrt, techniker, updatedAt } ] }`
Die App merged LWW über `updatedAt` und behält die lokale ID
(Unterschriften bleiben damit erhalten).

## GET /api/sync/files
Antwort: `{ "files": [ { path, size } ] }` — relative Pfade unter dem
Dateispeicher, z. B. `A4_01a/20260715/A4_01a_20260715_nah_101530.jpg`.
Die App lädt nur Dateien hoch, deren `path|size`-Kombination fehlt.

## PUT /api/sync/file?path=<urlencoded>
Body: rohe Dateibytes (`Content-Type: application/octet-stream`).
Antwort: `{ "ok": true }`. Pfade sind gegen `..`-Ausbruch abzusichern.


## Voll-Synchronisation (ab App v1.8.1) – Replace-All, Handy → Server

- `POST /api/sync/sperren`     Body `{ "sperren": [ { roomId, gesperrtAm, grund } ] }`
- `POST /api/sync/pruefpunkte` Body `{ "punkte": [ { titel, aktiv, sortIndex } ] }`
- `POST /api/sync/aktivitaet`  Body `{ "eintraege": [ { roomId, zeitpunkt, aktion } ] }`

Semantik: Server ersetzt den Tabelleninhalt komplett durch den App-Stand
(Antwort `{ "ok": true }`). Unterschriften-PNGs kommen über den normalen
Datei-Upload unter dem Pfadpräfix `_signaturen/`. Lesend fürs Web:
GET /api/web/sperren, /api/web/aktivitaet.
Die App toleriert Server ohne diese Endpunkte (Hinweis in der Sync-Meldung).

## Material-Bestand bidirektional (ab App v2.0-Beta) – LWW

`material` ist NICHT mehr Replace-All, sondern bidirektional (last-write-wins
über `updatedAt`, gleiche Semantik wie `rooms`). So sieht der Chef im Web-Lager,
wie viel z. B. an Antennen im Bestand ist, und umgekehrt landen im Web
geänderte Bestände zurück auf den Geräten.

- `POST /api/sync/material` Body `{ "material": [ { name, bestand, bestandAktiv,
  aktiv, sortIndex, updatedAt } ] }` — Upsert je `name`, nur wenn `updatedAt`
  neuer ist. Antwort `{ "ok": true, "uebernommen": n }`.
- `GET /api/sync/material` Antwort `{ "material": [ { name, bestand, bestandAktiv,
  aktiv, sortIndex, updatedAt } ] }` — die App merged LWW über `updatedAt`.

### GET /api/sync/lieferanten (ab App v2.0-Beta) – nur lesen
Antwort `{ "lieferanten": [ { name, kontakt, telefon, email, kundennummer,
notiz } ] }` – aktive Lieferanten aus dem Web-Lager, damit die App sie
anzeigen kann (keine Bearbeitung in der App).

Brücke zum Web-Lager: Ist ein `lager_artikel` über `app_material_name` mit dem
Material-Namen verknüpft, spiegelt der Server den Bestand in beide Richtungen
(App-Änderung → `lager_artikel.bestand` + Korrektur-Buchung „App-Sync“;
Lager-Buchung → `material.bestand` + neuer `updated_at`). Gespiegelt wird nur
bei echter Wertänderung, daher kein Ping-Pong. `updatedAt` ist wie bei `rooms`
lokale naive ISO-Zeit (`YYYY-MM-DDTHH:MM:SS`); die Web-Seite erzeugt den
Zeitstempel in `Europe/Berlin`, damit der String-Vergleich zur App passt.

## Mehrbenutzer-Betrieb (ab App v1.9)

`POST /api/sync/inspections`: jede Inspection trägt zusätzlich die optionalen
Felder `mitarbeiter` (String, wer geprüft hat) und `geloescht` (bool,
Papierkorb). Der Server upsertet über `uuid` und übernimmt bei Konflikt
`geloescht`/`mitarbeiter` (damit Papierkorb-Änderungen alle Geräte erreichen).

### GET /api/sync/inspections[?since=<Zeitstempel>]
Delta-Pull der Berichte ALLER Geräte. Ohne `since` alles, sonst nur Zeilen mit
`created_at > since` (Server-Empfangszeit, ISO). Antwort:
`{ "inspections": [ { uuid, roomId, datum, punkte, arbeiten, bemerkungen,
mitarbeiter, geloescht } ] }` — gleiche Punkte-Struktur wie beim POST.
Die App dedupliziert lokal über `uuid` (Berichte der Kollegen erscheinen so
auf jedem Gerät; Fotos der Kollegen werden bewusst NICHT übertragen).

### GET /api/sync/mitarbeiter
Antwort: `{ "mitarbeiter": [ { name, aktiv } ] }` — die im Web gepflegte
Mitarbeiterliste; die App bietet sie bei der Geräteeinrichtung zur Auswahl an.

### GET/POST /api/sync/zettel-eintraege (Team-Stundenzettel)
Eine Zeile je (station, zeitraumStart, mitarbeiter) mit `stunden`, `anfahrt`,
`updatedAt`. GET liefert alle (`{ "eintraege": [...] }`), POST upsertet mit
LWW über `updatedAt` (Antwort `{ "uebernommen": n }`). So rechnen mehrere
Mitarbeiter zeitgleich auf einer Station ab — ein Zettel, eine Zeile pro Kopf.

## Koordination & Lager (ab App v1.9.5)

### GET /api/sync/naechste-auftragsnummer
Antwort: `{ "auftragsnummer": "A-2026-0007" }` — nächste freie Nummer über
alle Geräte und das Web hinweg. Die App fragt sie beim Anlegen eines neuen
Stundenzettels ab; ohne Verbindung vergibt sie wie bisher lokal (Fallback).

### GET /api/sync/lager-status
Antwort: `{ "knapp": [ { bezeichnung, bestand, mindestbestand, einheit } ] }`
— aktive Lager-Artikel unter Mindestbestand. Die App zeigt sie als
Nachbestell-Warnung in der Verwaltung. Ältere Server ohne den Endpunkt
werden toleriert (Warnliste bleibt dann unverändert).

## KI-Fotoerkennung (ab App v2.0-Beta)

Der Server analysiert automatisch jedes hochgeladene Foto (eigener Service
`server/ki/`, OCR + eigene neuronale Netze) und vergleicht erkannte Werte
(Seriennummer, Freenet-ID, Gültig-bis, TV-Typ) mit den Zimmer-Stammdaten.
Die App kann diese Auswertung genauso einsehen und bestätigen wie das
Web-Panel — jede Entscheidung wird ein Trainingsbeispiel für die Netze.

### GET /api/sync/ki/analysen[?status=abweichung|uebereinstimmung|unlesbar|wartet|fehler][&room=<roomId>]
Antwort: `{ "analysen": [ { id, pfad, roomId, bildtyp, felder, abgleich,
status, modellVersion, fehler, erstelltAm, analysiertAm } ] }`
- `felder`: je erkanntem Feld `{ wert, konfidenz }`
- `abgleich`: je Feld `{ stammdaten, passt: true|false|null }`
- `bildtyp`: `menue` / `geraet` / `uebersicht`

### POST /api/sync/ki/analysen/:id/bestaetigen
Body: `{ "entscheidungen": { "<feld>": { "wert": "...", "stammdatenUebernehmen": true|false } } }`
Felder: `seriennummer`, `freenet_id`, `gueltig_bis`, `tv_typ`. Jede
Entscheidung wird Trainingslabel; bei `stammdatenUebernehmen: true` werden
die Zimmer-Stammdaten direkt korrigiert (setzt `updatedAt`, kommt beim
nächsten Rooms-Sync auf alle Geräte). Antwort `{ "ok": true }`.

### POST /api/sync/ki/analysen/:id/neu
Foto erneut analysieren lassen (z. B. nach einem Training). Antwort `{ "ok": true }`.

### GET /api/sync/ki/status
Warteschlange + aktive Modelle des KI-Service durchgereicht (`{ "offline": true }`
wenn der Service gerade nicht erreichbar ist — Analysen laufen später nach).

### GET /api/sync/file?path=<urlencoded>
Liest EIN gezielt angefordertes Foto (Rohbytes), z. B. um es in der KI-Prüfung
anzuzeigen. Bewusst kein Massen-Download-Endpunkt — die App lädt nur genau
das Foto, das gerade zur Prüfung geöffnet wird. Signaturen sind gesperrt (403).

## App-Verteilung & In-App-Updates (ab v1.9)

- `GET /app/version.json` (öffentlich, ohne Auth):
  `{ "versionCode": n, "versionName": "…" }` — die App vergleicht beim Sync
  gegen ihre eigene Version und zeigt bei neuerem Stand ein Update-Banner.
- `GET /app/kkh-tv-wartung.apk` (öffentlich): aktuelle signierte APK; die App
  lädt sie herunter und startet den Android-Installationsdialog.
  Beide Dateien liegen in `server/backend/public/app/` und kommen per
  Git-Commit + Autodeploy auf den Server. Download-Link für neue Geräte:
  `https://riegel-troisdorf.de/kkh/app/kkh-tv-wartung.apk`.

## Fehlerformat
Fehler immer `{ "error": "Beschreibung" }` mit passendem HTTP-Status;
die App zeigt `error` dem Nutzer an. 401 = ungültiger API-Schlüssel.

## Wichtig für den Reverse-Proxy
`client_max_body_size 300m` (Foto-Uploads) und die Sync-Pfade dürfen
NICHT hinter Basic-Auth liegen (App authentifiziert nur per X-Api-Key).

---
Änderungswünsche an diesem Vertrag bitte hier im Dokument ändern UND der
jeweils anderen Seite mitteilen (App-Seite: Claude-Session im App-Repo).
