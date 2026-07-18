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
- `POST /api/sync/material`    Body `{ "material": [ { name, bestand, bestandAktiv, aktiv, sortIndex } ] }`
- `POST /api/sync/pruefpunkte` Body `{ "punkte": [ { titel, aktiv, sortIndex } ] }`
- `POST /api/sync/aktivitaet`  Body `{ "eintraege": [ { roomId, zeitpunkt, aktion } ] }`

Semantik: Server ersetzt den Tabelleninhalt komplett durch den App-Stand
(Antwort `{ "ok": true }`). Unterschriften-PNGs kommen über den normalen
Datei-Upload unter dem Pfadpräfix `_signaturen/`. Lesend fürs Web:
GET /api/web/material, /api/web/sperren, /api/web/aktivitaet.
Die App toleriert Server ohne diese Endpunkte (Hinweis in der Sync-Meldung).

Datei-Pfadpräfixe im Dateispeicher (alles über GET files / PUT file):
- `<Zimmer-ID>/<JJJJMMTT>/…` – Fotos UND das Prüfbericht-PDF des Tages
  (`Pruefbericht_<Zimmer>_<JJJJMMTT>.pdf`); das PDF wird von der App bei jedem
  gespeicherten Prüfbogen und jeder Fotoänderung neu erzeugt.
- `_signaturen/` – Unterschriften-PNGs der Stundenzettel.
- `_stundenzettel/` – fertige, unterschriebene Stundenzettel-PDFs
  (`Stundenzettel_<Station>_<ZeitraumStart>.pdf`), bei jedem PDF-Export
  aktualisiert. Fürs Web direkt verlinkbar über GET /api/web/file?path=….

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
