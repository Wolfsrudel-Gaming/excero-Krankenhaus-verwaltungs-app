# KI-Fotoerkennung für das KKH-System

Eigener Docker-Service, der alle Prüfungsfotos automatisch analysiert:
Er liest aus CI-Menü-Fotos die **Seriennummer**, **Freenet-ID** und das
**Gültigkeitsdatum**, erkennt aus Übersichtsfotos den **TV-Typ** und gleicht
alles mit den Zimmer-Stammdaten und den Angaben des Monteurs ab.
Abweichungen erscheinen im Web-Panel unter **KI-Prüfung**.

## Funktionsweise

```
Foto-Upload (App) → Warteschlange (foto_analysen, Status 'wartet')
   → KI-Worker holt Job
      → 1. Bildklassifikation   (eigenes CNN: CI-Menü / Gerät / Übersicht + TV-Typ)
      → 2. Texterkennung        (EasyOCR, Deutsch + Englisch, CPU)
      → 3. Feld-Zuordnung       (eigenes MLP + Format-Regeln)
      → 4. Abgleich             (erkannt vs. Stammdaten in rooms)
   → Ergebnis in foto_analysen (Status: uebereinstimmung / abweichung / unlesbar)
```

### Die eigenen neuronalen Netze

| Netz | Aufgabe | Architektur | Datei |
|------|---------|-------------|-------|
| Klassifikator | Bildtyp (Menü/Gerät/Übersicht) + TV-Typ | MobileNetV3-Small (eingefroren) + 2 eigene Köpfe | `models/klassifikator.py` |
| Feldnetz | OCR-Zeile → Feldtyp (SN/Freenet-ID/Datum/irrelevant) | MLP (16 Merkmale → 64 → 32 → 4) | `models/feldnetz.py` |

**Bootstrap-Verhalten:** Bevor die Netze trainiert sind, arbeitet die Pipeline
mit Heuristik (Dateiname `_nah_`/`_fern_`) und harten Format-Regeln
(Freenet-ID = 11 Ziffern, SN = 9–14 Ziffern oder alphanumerisch,
Datum TT.MM.JJJJ). Das liefert vom ersten Tag an Ergebnisse.

### Lernen aus der Praxis (das Kernprinzip)

Trainingsdaten entstehen auf zwei Wegen:

1. **Auto-Labels:** Findet die OCR den in den Stammdaten hinterlegten Wert im
   Foto wieder, ist das ein sicheres Trainingsbeispiel (Selbst-Matching).
2. **Web-Bestätigungen:** Jede Entscheidung im Web-Panel („KI hat recht" /
   „Stammdaten stimmen" / manueller Wert) wird als Label gespeichert.

Das Training läuft **automatisch jede Nacht um 3:30 Uhr** und kann im
Web-Panel („Training starten") oder per `POST /train` angestoßen werden.
Modelle werden versioniert unter `/data/ki-models/` abgelegt (Docker-Volume
`kkh_ki_models`); die Pipeline lädt nach dem Training automatisch die neue
Version. Mindest-Labelzahlen: 20 (Klassifikator), 15 (Feldnetz) – vorher wird
das Training übersprungen und die Regeln bleiben aktiv.

## Datenschutz

- Fotos aus `_signaturen/` werden **niemals** analysiert (mehrfach geprüft:
  Backend-Einreihung, Service-Endpunkt, Start-Scan).
- Der Service ist **nur intern** im Docker-Netz erreichbar, kein Port nach außen.
- Foto-Volume ist **read-only** eingebunden.

## API (intern, `http://ki:8100`)

| Endpunkt | Beschreibung |
|----------|--------------|
| `GET /status` | Warteschlange, aktive Modelle, Label-Anzahl |
| `POST /analyse` | `{pfad, room_id}` – Foto (erneut) einreihen |
| `POST /train` | Training sofort starten |

Das Web-Panel spricht den Service über das Node-Backend an
(`/kkh/api/web/ki/...`, siehe Backend-`index.js`).

## Datenbank-Tabellen (gemeinsame PostgreSQL)

- `foto_analysen` – Warteschlange + Ergebnisse (Felder als JSONB inkl. OCR-Rohtext)
- `ki_labels` – bestätigte Wahrheit je Foto/Feld (die Trainingsdaten)
- `ki_modelle` – Modell-Historie mit Validierungs-Genauigkeit

## Betrieb

```bash
# Bauen + starten (aus server/)
docker compose build ki && docker compose up -d ki

# Logs verfolgen
docker compose logs -f ki

# Training manuell (im Container)
docker compose exec ki python training.py
```

**Ressourcen:** max. 4 CPU-Kerne, 6 GB RAM (Compose-Limits). Eine Foto-Analyse
dauert auf CPU ca. 5–15 Sekunden – für den Anwendungsfall (Fotos kommen über
den Tag verteilt) völlig ausreichend.

## Verbesserungs-Fahrplan (TODOs)

1. **App: dritter Foto-Button „CI-Menü"** – gezielt markierte Menü-Fotos
   machen die Bildklassifikation überflüssig sicher (siehe ARCHITECTURE.md).
2. **Feinjustierung nach ~200 Labels:** Backbone teilweise auftauen
   (letzte Blöcke), Lernrate 1e-4 – hebt die TV-Typ-Genauigkeit deutlich.
3. **Erkennungs-Feedback in die App:** Wenn die KI eine Abweichung findet,
   könnte der Monteur direkt beim nächsten Sync einen Hinweis bekommen.
4. **Bild-Vorverarbeitung erweitern:** Perspektivkorrektur für schräg
   fotografierte Bildschirme (OpenCV `getPerspectiveTransform` über die
   hellste Rechteck-Kontur) – hebt die OCR-Quote bei Menü-Fotos.
