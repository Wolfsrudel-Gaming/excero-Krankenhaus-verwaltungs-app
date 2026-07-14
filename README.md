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
