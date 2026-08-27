# Play Store – Anleitung, Store-Texte & Datensicherheit

Alles, was du zum Hochladen der App in den Google Play Store (interner Test)
brauchst. Das fertige, signierte **App Bundle** ist `app-release.aab`
(versionCode 26, versionName 2.0-beta6, signiert mit dem EXCERO-Schlüssel).

---

## 0. Voraussetzungen (einmalig)

1. **Google-Entwicklerkonto** anlegen: <https://play.google.com/console>
   → 25 $ einmalig, Konto z. B. als **Wolfsrudel Media Studio** (Privatperson/
   Einzelunternehmer reicht, nur Ausweis-Verifizierung).
2. **Neue App erstellen** → Name „KKH TV-Wartung", Sprache Deutsch, „App",
   „Kostenlos".
3. **Datenschutz-URL** bereitstellen: `store/datenschutz.html` ausfüllen und auf
   den Server legen (z. B. nach `server/backend/public/datenschutz.html`, dann
   erreichbar unter `https://riegel-troisdorf.de/kkh/datenschutz.html`).

---

## 1. App Bundle hochladen (interner Test)

1. In der Play Console: **Testen → Interner Test → Neue Version erstellen**.
2. **Play App Signing** akzeptieren (Google verwaltet den Auslieferungs-
   schlüssel; unser EXCERO-Schlüssel ist der Upload-Schlüssel).
3. `app-release.aab` hochladen.
4. **Tester** hinzufügen: E-Mail-Liste mit den Google-Konten der Kollegen
   (bis zu 100). Nur diese können die App später über den Play Store
   installieren.
5. Version zur Prüfung freigeben. Beim internen Test ist die Freigabe meist
   in Minuten bis wenigen Stunden da.
6. Den **Test-Link** (Opt-in-URL) an die Kollegen schicken → einmal beitreten,
   danach kommt die App und alle Updates automatisch über den Play Store.

> **Wichtig zur Umstellung:** Die aktuell seitlich installierte, EXCERO-signierte
> App wird **nicht** automatisch zur Play-Version. Kollegen einmal: vorher
> synchronisieren/Backup, alte App deinstallieren, dann über den Play-Test-Link
> neu installieren, Server-URL + API-Schlüssel + Name eintragen → Daten kommen
> per Sync zurück.

---

## 2. Store-Eintrag (Texte zum Kopieren)

**App-Name (max. 30 Zeichen):**
```
KKH TV-Wartung
```

**Kurzbeschreibung (max. 80 Zeichen):**
```
Internes Werkzeug zur Wartung der TV-Geräte im Kinderkrankenhaus Köln.
```

**Vollständige Beschreibung:**
```
KKH TV-Wartung ist ein internes Arbeitswerkzeug der EXCERO GmbH für die
Wartung der Fernsehgeräte im Kinderkrankenhaus Köln.

Funktionen:
• Digitaler Prüfbogen für jedes Zimmer (Empfang, Seriennummer, Freenet-ID,
  Fernbedienung, Halterung u. a.)
• Foto-Dokumentation je Gerät
• Freenet-Ablaufübersicht und Statistik
• Team-Stundenzettel mit Unterschrift auf dem Gerät
• Materialbestand mit Nachbestell-Warnung
• Automatischer Abgleich mit dem EXCERO-Server

Die App richtet sich ausschließlich an die Technikerinnen und Techniker der
EXCERO GmbH und ist nicht für die allgemeine Nutzung bestimmt.
```

**Grafiken (musst du noch erstellen):**
- App-Icon 512×512 px (liegt als Launcher-Icon vor, in 512 exportieren).
- Feature-Grafik 1024×500 px (einfacher Banner mit Logo genügt).
- Mind. 2 Screenshots vom Handy (Dashboard, Prüfbogen, Zimmerliste).

**Kategorie:** Produktivität oder Unternehmen.
**Kontakt-E-Mail:** [deine/EXCERO-Adresse]

---

## 3. Datensicherheits-Formular (so ausfüllen)

Play Console → **App-Inhalt → Datensicherheit**. Vorgeschlagene Antworten
passend zur App:

| Frage | Antwort |
|---|---|
| Erhebt oder teilt die App Nutzerdaten? | **Ja** (Daten werden erhoben) |
| Werden Daten **an Dritte weitergegeben**? | **Nein** (nur an euren eigenen Server) |
| Werden Daten **bei der Übertragung verschlüsselt**? | **Ja** (HTTPS) |
| Können Nutzer **Löschung beantragen**? | **Ja** (über den Verantwortlichen) |

**Erhobene Datentypen (ankreuzen):**
- **Personenbezogene Daten → Name** (Mitarbeitername): Zweck „App-Funktionalität".
- **Fotos** (Geräte-Fotos): Zweck „App-Funktionalität".
- **Sonstige Daten** (Unterschrift, Arbeitszeiten): Zweck „App-Funktionalität".

Nicht erhoben: Standort, Kontakte, Finanzdaten, Gesundheitsdaten, Werbe-ID,
Nutzungs-/Analysedaten.

---

## 4. Weitere Pflicht-Formulare (schnell)

- **Alterseinstufung:** IARC-Fragebogen ausfüllen → wird „Ab 3 / USK 0".
- **Zielgruppe:** Erwachsene (nicht für Kinder).
- **Werbung:** „Nein, enthält keine Werbung".
- **Regierungs-App / COVID / Finanz:** Nein.

---

## 5. Aufwand-Zusammenfassung

| Schritt | Zeit |
|---|---|
| Konto + Verifizierung | Minuten Arbeit, ggf. Tage Wartezeit |
| Bundle hochladen + Tester + Formulare | ~1–2 Stunden |
| Grafiken (Icon/Banner/Screenshots) | ~1 Stunde |
| Interne-Test-Freigabe | Minuten bis wenige Stunden |
| Jedes künftige Update | Bundle bauen + hochladen, ~15 Min |
