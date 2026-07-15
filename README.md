# KKH TV-Wartung

Android-App für die Wartung der Fernseher im Kinderkrankenhaus Köln.
Sie ersetzt den Papier-Prüfbogen („Prüfung TV-Empfangsgeräte") und den
manuellen Abgleich mit der Excel-Liste (KKH-Übersicht).

## Funktionen

- **Zimmerübersicht:** Alle Stationen und Zimmer aus der KKH-Übersicht,
  mit Suche (Zimmer, Station, Seriennummer, Freenet-ID) und Ampel-Status
  für die Freenet-Gültigkeit (rot = abgelaufen, gelb = weniger als 3 Monate,
  grün = in Ordnung). Bereits geprüfte Zimmer werden pro Tag abgehakt.
- **Hinterlegte Daten je Zimmer:** TV-Typ, Seriennummer, Freenet TV-ID,
  Gültigkeitsdatum, letzte Prüfung und der komplette Lebenslauf –
  direkt in der App änderbar.
- **Digitaler Prüfbogen:** Die Prüfpunkte des Papierbogens
  (Empfang, Seriennummer, Freenet TV-ID, DVD-Test, Fernbedienung, Halterung,
  Gültigkeit > 3 Monate, Freenet verlängert) mit i.O./n.i.O.-Schaltern und
  Bemerkungsfeldern. Abweichende Seriennummern/IDs können per Häkchen direkt
  als neue Stammdaten übernommen werden. Beim Speichern wird automatisch ein
  Lebenslauf-Eintrag erzeugt (anpassbar), die letzte Prüfung gesetzt und bei
  Verlängerung das neue Gültigkeitsdatum übernommen.
- **Fotos aus der App:** Je Zimmer „Foto fern" und „Foto nah" über die
  Systemkamera. Die Bilder landen automatisch in der HiDrive-Ordnerstruktur
  `Fotos_Zimmer/<Station_Zimmer>/<JJJJMMTT>/`.
- **ZIP am Tagesende:** Ein Klick erzeugt eine ZIP mit allen Ordnern
  (wahlweise nur der heutige Tag oder alles), die nur noch in den HiDrive
  gezogen werden muss.
- **Excel als Schnittstelle:** Die App nutzt intern eine richtige Datenbank
  (Room/SQLite) und kann jederzeit
  - die aktuelle **KKH-Übersicht (.xlsx) importieren** (Stammdaten aus Tabelle1)
  - und eine **.xlsx exportieren** mit dem Blatt „Übersicht" im gewohnten
    Spaltenformat plus einem Blatt „Prüfprotokolle" mit allen ausgefüllten
    Prüfbögen. Datumswerte sind echte Excel-Datumszellen (TT.MM.JJJJ).
- **Startdaten:** Der Stand der hochgeladenen KKH-Übersicht (101 Zimmer)
  ist als Ausgangsdatenbank in der App enthalten.

### Neu in Version 1.6.1

- **Stundenzettel-Verwaltung:** Auf der Export-Seite listet „Gespeicherte
  Stundenzettel" alle Zettel mit Status-Badges (Stunden eingetragen?
  unterschrieben?) – antippen zum Bearbeiten und PDF-Export. Ältere Zettel
  behalten ihr korrektes Leistungs-Zeitfenster.
- **Stunden statt von/bis:** Auf dem Stundenzettel werden die Arbeitsstunden
  direkt eingetragen (z. B. „3,5"), kein Start/Ende mehr.
- **Unterschriften werden gespeichert:** Unterschrift auf der Station einholen
  und mit „Speichern" sichern – die Stunden lassen sich später eintragen und
  erst dann das PDF erzeugen. Gespeicherte Unterschriften werden angezeigt
  und können durch neues Unterschreiben ersetzt werden.

### Neu in Version 1.6

- **Firmenlogo (EXCERO GmbH)** im Kopf von Prüfbericht- und Stundenzettel-PDF.
- **Zimmer & Stationen anlegen:** Plus-Button in der Übersicht; ein neuer
  Stationsname legt die Station automatisch mit an. Zimmer lassen sich als
  **inaktiv** archivieren (Historie bleibt, ausblendbar/reaktivierbar).
- **Materialkatalog & Lagerbestand:** Neuer Verwaltungsbereich (Koffer-Symbol);
  Arbeiten/Material frei anleg-, umbenenn- und ausblendbar, je Eintrag optional
  Bestandsführung – der Bestand wird beim Speichern eines Prüfbogens
  automatisch reduziert und im Prüfbogen-Chip angezeigt.
- **Eigene Prüfpunkte:** Zusätzliche Prüfpunkte erscheinen im Prüfbogen unter
  den Standardpunkten und wandern in Bericht, PDF und Excel-Bemerkungen.
- **Stundenzettel gespeichert & Auftragsnummer:** Zeiten/Techniker werden je
  Station und Zeitraum gespeichert und sind später änderbar ("Nur speichern");
  jede Anfahrt erhält eine fortlaufende Auftragsnummer (z. B. A-2026-0001),
  die auf dem PDF erscheint.
- **"Kein Zutritt" mit Grund:** Optionales Grund-Feld (z. B. Isolation),
  erscheint im Lebenslauf-Vermerk.
- Durchgeführte Arbeiten stehen jetzt auch im Excel-Blatt "Prüfprotokolle".

### Neu in Version 1.5

- **Fotos direkt im Prüfbogen und Prüfbericht:** Der Fotobereich (Kamera
  fern/nah, Galerie, Vorschau, Löschen) ist jetzt auch im Prüfbogen und in
  der Prüfbericht-Ansicht eingebettet – Fotos entstehen ohne Screenwechsel,
  weitere Bilder lassen sich jederzeit nachträglich hinzufügen (beim Bericht
  im richtigen Tagesordner).
- **Stundenzettel mit Zeiten und digitaler Unterschrift:** Vor dem PDF-Export
  werden Datum, Arbeitszeit (von–bis, Stunden werden berechnet), Anfahrt und
  Name erfasst; Station und Dienstleister unterschreiben direkt auf dem
  Display. Beides erscheint im PDF (Zeiten-Block und eingebettete
  Unterschriften über den Signaturlinien).
- **„Kein Zutritt" im Lebenslauf:** Wird ein Zimmer als nicht betretbar
  markiert, erhält es automatisch einen Lebenslauf-Eintrag mit aktuellem
  Datum („Zimmer konnte nicht betreten werden"); beim Aufheben der Sperre am
  selben Tag wird der Vermerk wieder entfernt.

### Neu in Version 1.4

- **Durchgeführte Arbeiten / Material erfassen:** Im Prüfbogen lässt sich
  per Chips ankreuzen, was gemacht bzw. verbaut wurde (Fernbedienung,
  Antenne, CI-Modul, TV-Tausch, Sendersuchlauf, Kabel, Halterung,
  Neueinrichtung; Freenet-Verlängerung wird automatisch übernommen) plus
  Freitext. Das fließt in Lebenslauf, Prüfbericht-PDF und Stundenzettel ein.
- **Stundenzettel pro Station (PDF):** Über das Formular-Symbol neben der
  Station in der Übersicht wird ein Leistungsnachweis für den aktuellen
  Prüfzeitraum erzeugt – mit Auftraggeber (Kinderklinik Köln, Amsterdamer
  Straße 59, 50735 Köln) und Station, je Zimmer den durchgeführten
  Arbeiten, einer Materialzusammenfassung (Stückzahlen) und zwei
  Unterschriftfeldern (Station und Dienstleister) zum Unterschreiben-Lassen.

### Neu in Version 1.3.1

- **PDF im Bilderordner:** Beim ZIP-Export wird zu jedem geprüften Zimmer
  automatisch das Prüfbericht-PDF in seinen Tagesordner
  (`Fotos_Zimmer/<Station_Zimmer>/<JJJJMMTT>/`) gelegt – es wandert damit
  zusammen mit den Fotos in die ZIP und in den HiDrive.

### Neu in Version 1.3

- **Prüfberichte als PDF:** Jeder gespeicherte Prüfbogen lässt sich als
  ansprechend gestaltetes A4-PDF exportieren – mit Kopfbereich, Stammdaten,
  Prüfpunkte-Tabelle (i.O./n.i.O.-Chips), Bemerkungen und den Fotos des
  Prüftags (nativ erzeugt, ohne Zusatzbibliotheken).
- **Prüfbericht-Archiv in der App:** In den Zimmerdetails listet die Karte
  „Prüfberichte" alle bisherigen Bögen; jeder Bericht ist jederzeit
  einsehbar (Prüfpunkte, Bemerkungen, Fotos) und einzeln exportierbar.
- **Tages-PDF:** Auf der Export-Seite lassen sich alle heute ausgefüllten
  Prüfbögen in ein gemeinsames PDF exportieren.

### Neu in Version 1.2

- **„Kein Zutritt"-Vermerke:** Nach der Anmeldung bei der Stationsschwester
  über das Tür-Symbol neben der Station die gesperrten Zimmer ankreuzen.
  Diese Zimmer erscheinen rot in der Übersicht (Badge „KEIN ZUTRITT"),
  der Prüfbogen ist dort gesperrt, und in den Zimmerdetails erscheint ein
  Warnbanner. Die Vermerke gelten für den aktuellen Prüfzeitraum und
  laufen bei der nächsten Anfahrt automatisch ab; sie lassen sich auch
  einzeln wieder aufheben.

### Neu in Version 1.1

- **Einstellbarer Prüfzeitraum („eine Anfahrt"):** Nur heute, diese Woche
  oder seit einem festen Datum – bestimmt, welche Zimmer in der Übersicht
  als geprüft abgehakt werden.
- **Stationsreihenfolge nach Lage:** A2, B2, A3, B3, A4, B4, A5, B5, …;
  alle übrigen Stationen (C, D, E, F, Not, …) am Ende.
- **TV-Marke im Prüfbogen änderbar** mit Schnellauswahl bekannter Marken.
- **Gültigkeitsdatum korrigierbar** direkt im Prüfbogen (bei „Gültigkeit
  n.i.O.") und wie bisher in den Stammdaten.
- **Freenet-Links:** „Verlängern" (Shop) und „Aktivieren" (neues
  Aktivierungssignal) öffnen die Freenet-Webseite direkt aus der App.
- **Bilder aus der Galerie** zusätzlich zur Kamera-Aufnahme.
- **Präzisere Lebenslauf-Einträge:** Bei Übernahme neuer Werte steht
  „Seriennummer angepasst" / „Freenet-ID angepasst" statt nur „abweichend".
- **Duplikat-Warnung:** Ist eine Freenet-ID oder TV-Seriennummer bereits
  bei einem anderen Zimmer hinterlegt, erscheint ein gelber Hinweis.
- **Internes Aktivitätsprotokoll:** Wann welches Zimmer bearbeitet wurde
  (mit Uhrzeit) – einsehbar je Zimmer und gesamt in den Einstellungen,
  wird nicht exportiert.

**Update-Hinweis:** Die APK ist mit dem im Repo hinterlegten Schlüssel
(`keystore/debug.keystore`) signiert. Neue Versionen installieren sich
dadurch als Update über die bestehende App; Datenbank und Fotos bleiben
erhalten (Room-Migration).

## Typischer Tagesablauf

1. Zimmer in der Übersicht antippen → hinterlegte Daten mit den
   CI-Informationen am Fernseher abgleichen.
2. „Prüfbogen ausfüllen" → Prüfpunkte abhaken, ggf. Werte korrigieren,
   speichern.
3. „Foto fern" und „Foto nah" aufnehmen.
4. Am Tagesende: Export-Seite → „ZIP erstellen" → Datei in den HiDrive
   hochladen. Optional „Excel exportieren" für die aktualisierte Übersicht.

## Build

Voraussetzungen: Android SDK (API 35), JDK 17+.

```bash
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Mindest-Android-Version: 8.0 (API 26). Es werden keine besonderen
Berechtigungen benötigt (Kamera über die System-Kamera-App, Dateiablage im
app-eigenen Speicher, Export über den Android-Dateidialog).

## Technik

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Room (SQLite) als Datenbank, Coil für Foto-Vorschauen
- Excel-Import/-Export ohne externe Bibliotheken
  (eigener minimaler OOXML-Reader/-Writer, per Round-Trip-Test abgesichert)
