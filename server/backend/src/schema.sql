-- Excero Webapp – Serverdatenbank (PostgreSQL)
-- Mandantenfähiges internes Firmensystem (Excero GmbH + Wolfsrudel Media Studio)
-- KKH TV-Wartung ist ein Modul davon.
-- Alle Tabellen werden per CREATE TABLE IF NOT EXISTS additiv ergänzt.

CREATE TABLE IF NOT EXISTS rooms (
    id              TEXT PRIMARY KEY,          -- z. B. A4_01a
    station         TEXT NOT NULL,
    zimmer          TEXT NOT NULL,
    lebenslauf      TEXT NOT NULL DEFAULT '',
    letzte_pruefung TEXT NOT NULL DEFAULT '',  -- ISO-Datum
    tv_typ          TEXT NOT NULL DEFAULT '',
    seriennummer    TEXT NOT NULL DEFAULT '',
    freenet_id      TEXT NOT NULL DEFAULT '',
    gueltig_bis     TEXT NOT NULL DEFAULT '',  -- ISO-Datum
    inaktiv         BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at      TEXT NOT NULL DEFAULT ''   -- ISO-Datum+Zeit (LWW-Sync)
);

CREATE TABLE IF NOT EXISTS inspections (
    uuid       TEXT PRIMARY KEY,               -- geräteübergreifend eindeutig
    room_id    TEXT NOT NULL,
    datum      TEXT NOT NULL,                  -- ISO-Datum
    daten      JSONB NOT NULL,                 -- punkte, arbeiten, bemerkungen
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_inspections_room ON inspections(room_id, datum);

CREATE TABLE IF NOT EXISTS stundenzettel (
    station        TEXT NOT NULL,
    zeitraum_start TEXT NOT NULL,
    auftragsnummer TEXT NOT NULL DEFAULT '',
    datum          TEXT NOT NULL DEFAULT '',
    stunden        TEXT NOT NULL DEFAULT '',
    anfahrt        TEXT NOT NULL DEFAULT '',
    techniker      TEXT NOT NULL DEFAULT '',
    updated_at     TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (station, zeitraum_start)
);

-- Benutzer der Weboberfläche (Mehrbenutzer-Login mit vollem Zugriff).
-- Passwörter werden mit scrypt + zufälligem Salt gehasht (nie im Klartext).
CREATE TABLE IF NOT EXISTS users (
    id            SERIAL PRIMARY KEY,
    username      TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    salt          TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Login unabhängig von Groß-/Kleinschreibung des Benutzernamens
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower ON users (lower(username));

-- Voll-Synchronisation: Spiegel der App-Daten (Anzeige im Web optional)
CREATE TABLE IF NOT EXISTS sperren (
    room_id     TEXT PRIMARY KEY,
    gesperrt_am TEXT NOT NULL,
    grund       TEXT NOT NULL DEFAULT ''
);
CREATE TABLE IF NOT EXISTS material (
    name          TEXT PRIMARY KEY,
    bestand       NUMERIC(14,2) NOT NULL DEFAULT 0,
    bestand_aktiv BOOLEAN NOT NULL DEFAULT FALSE,
    aktiv         BOOLEAN NOT NULL DEFAULT TRUE,
    sort_index    INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS app_pruefpunkte (
    titel      TEXT PRIMARY KEY,
    aktiv      BOOLEAN NOT NULL DEFAULT TRUE,
    sort_index INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS app_aktivitaet (
    id        SERIAL PRIMARY KEY,
    room_id   TEXT NOT NULL,
    zeitpunkt TEXT NOT NULL,
    aktion    TEXT NOT NULL
);

-- v1.9: Mehrbenutzer & Team-Stundenzettel
ALTER TABLE inspections ADD COLUMN IF NOT EXISTS mitarbeiter TEXT NOT NULL DEFAULT '';
ALTER TABLE inspections ADD COLUMN IF NOT EXISTS geloescht BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_inspections_created ON inspections(created_at);

CREATE TABLE IF NOT EXISTS mitarbeiter (
    name  TEXT PRIMARY KEY,
    aktiv BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS zettel_eintraege (
    station        TEXT NOT NULL,
    zeitraum_start TEXT NOT NULL,
    mitarbeiter    TEXT NOT NULL,
    stunden        TEXT NOT NULL DEFAULT '',
    anfahrt        TEXT NOT NULL DEFAULT '',
    updated_at     TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (station, zeitraum_start, mitarbeiter)
);

-- =====================================================================
-- Lager-Modul (unabhängig vom App-Prüfbogen-Material-Spiegel)
-- =====================================================================

CREATE TABLE IF NOT EXISTS lieferanten (
    id          SERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    kontakt     TEXT NOT NULL DEFAULT '',
    telefon     TEXT NOT NULL DEFAULT '',
    email       TEXT NOT NULL DEFAULT '',
    kundennummer TEXT NOT NULL DEFAULT '',
    notiz       TEXT NOT NULL DEFAULT '',
    aktiv       BOOLEAN NOT NULL DEFAULT TRUE,
    erstellt_am TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS lager_artikel (
    id               SERIAL PRIMARY KEY,
    bezeichnung      TEXT NOT NULL,
    artikelnummer    TEXT NOT NULL DEFAULT '',      -- SKU / Artikelnr.
    kategorie        TEXT NOT NULL DEFAULT '',
    einheit          TEXT NOT NULL DEFAULT 'Stk.',
    ek_preis         NUMERIC(10,2),                -- Einkaufspreis, NULL = unbekannt
    vk_preis         NUMERIC(10,2),                -- Verkaufspreis / Weiterberechnungspreis
    bestand          NUMERIC(14,2) NOT NULL DEFAULT 0,
    mindestbestand   NUMERIC(14,2) NOT NULL DEFAULT 0,
    lieferant_id     INTEGER REFERENCES lieferanten(id) ON DELETE SET NULL,
    app_material_name TEXT NOT NULL DEFAULT '',    -- Verknüpfung zur App-Checkliste
    aktiv            BOOLEAN NOT NULL DEFAULT TRUE,
    notiz            TEXT NOT NULL DEFAULT '',
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lager_artikel_kategorie ON lager_artikel(kategorie);
CREATE INDEX IF NOT EXISTS idx_lager_artikel_lieferant ON lager_artikel(lieferant_id);

CREATE TABLE IF NOT EXISTS lager_buchungen (
    id          SERIAL PRIMARY KEY,
    artikel_id  INTEGER NOT NULL REFERENCES lager_artikel(id) ON DELETE CASCADE,
    typ         TEXT NOT NULL CHECK (typ IN ('eingang','ausgang','korrektur')),
    menge       NUMERIC(14,2) NOT NULL,             -- positiv; Typ bestimmt Richtung
    ek_preis    NUMERIC(10,2),                      -- EK zum Buchungszeitpunkt
    grund       TEXT NOT NULL DEFAULT '',
    bezug       TEXT NOT NULL DEFAULT '',            -- z. B. Auftragsnummer / Zimmer
    benutzer    TEXT NOT NULL DEFAULT '',
    zeitpunkt   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lager_buchungen_artikel ON lager_buchungen(artikel_id);
CREATE INDEX IF NOT EXISTS idx_lager_buchungen_zeitpunkt ON lager_buchungen(zeitpunkt DESC);

-- =====================================================================
-- Excero Webapp: Mandanten, Einstellungen, Rechnungen, Finanzen, etc.
-- =====================================================================

-- Firmenprofil / Mandant
CREATE TABLE IF NOT EXISTS firmen (
    id               SERIAL PRIMARY KEY,
    name             TEXT NOT NULL,
    rechtsform       TEXT NOT NULL DEFAULT '',     -- GmbH, Einzelunternehmen, etc.
    adresse          TEXT NOT NULL DEFAULT '',
    steuernummer     TEXT NOT NULL DEFAULT '',
    ust_id           TEXT NOT NULL DEFAULT '',
    besteuerung      TEXT NOT NULL DEFAULT 'regel' CHECK (besteuerung IN ('regel','kleinunternehmer')),
    ust_satz         NUMERIC(5,2) NOT NULL DEFAULT 19.00,
    iban             TEXT NOT NULL DEFAULT '',
    bic              TEXT NOT NULL DEFAULT '',
    bank_name        TEXT NOT NULL DEFAULT '',
    stundensatz      NUMERIC(10,2),               -- Standard-Stundensatz für Abrechnung
    logo_pfad        TEXT NOT NULL DEFAULT '',     -- relativer Pfad zu /data/files
    rechnungs_prefix TEXT NOT NULL DEFAULT 'RE',
    rechnungs_fusstext TEXT NOT NULL DEFAULT '',
    email            TEXT NOT NULL DEFAULT '',
    telefon          TEXT NOT NULL DEFAULT '',
    webseite         TEXT NOT NULL DEFAULT '',
    aktiv            BOOLEAN NOT NULL DEFAULT TRUE,
    erstellt_am      TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Seed: Excero GmbH (Platzhalter-Daten, im UI editierbar) – nur einmalig
INSERT INTO firmen (name, rechtsform, besteuerung, rechnungs_prefix)
SELECT 'Excero GmbH', 'GmbH', 'regel', 'EX'
WHERE NOT EXISTS (SELECT 1 FROM firmen WHERE name = 'Excero GmbH');

-- Globale Einstellungen (Key/Value, JSONB-Wert)
CREATE TABLE IF NOT EXISTS einstellungen (
    key   TEXT PRIMARY KEY,
    wert  JSONB NOT NULL DEFAULT 'null'::jsonb
);
-- Leere Platzhalter für UI-Konfiguration
INSERT INTO einstellungen (key, wert) VALUES
    ('smtp', 'null'::jsonb),
    ('hidrive', 'null'::jsonb)
ON CONFLICT DO NOTHING;

-- Kunden (firmenübergreifend, zur Wiederverwendung in Rechnungen)
CREATE TABLE IF NOT EXISTS kunden (
    id          SERIAL PRIMARY KEY,
    firma_id    INTEGER REFERENCES firmen(id) ON DELETE SET NULL,
    name        TEXT NOT NULL,
    anrede      TEXT NOT NULL DEFAULT '',
    adresse     TEXT NOT NULL DEFAULT '',
    email       TEXT NOT NULL DEFAULT '',
    telefon     TEXT NOT NULL DEFAULT '',
    steuernummer TEXT NOT NULL DEFAULT '',
    ust_id      TEXT NOT NULL DEFAULT '',
    notiz       TEXT NOT NULL DEFAULT '',
    aktiv       BOOLEAN NOT NULL DEFAULT TRUE,
    erstellt_am TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_kunden_firma ON kunden(firma_id);

-- Baustellen / Projekte
CREATE TABLE IF NOT EXISTS baustellen (
    id          SERIAL PRIMARY KEY,
    firma_id    INTEGER REFERENCES firmen(id) ON DELETE SET NULL,
    kunde_id    INTEGER REFERENCES kunden(id) ON DELETE SET NULL,
    name        TEXT NOT NULL,
    adresse     TEXT NOT NULL DEFAULT '',
    status      TEXT NOT NULL DEFAULT 'aktiv' CHECK (status IN ('aktiv','pausiert','abgeschlossen')),
    beginn      TEXT NOT NULL DEFAULT '',          -- ISO-Datum
    ende        TEXT NOT NULL DEFAULT '',          -- ISO-Datum
    stundensatz NUMERIC(10,2),                    -- überschreibt Firmenprofil wenn gesetzt
    notiz       TEXT NOT NULL DEFAULT '',
    erstellt_am TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- KKH als Baustelle vordefiniert – nur einmalig
INSERT INTO baustellen (name, adresse, status, notiz)
SELECT 'KKH Amsterdamer Straße', 'Amsterdamer Straße 59, 50735 Köln', 'aktiv', 'TV-Wartung Kinderklinik Köln'
WHERE NOT EXISTS (SELECT 1 FROM baustellen WHERE name = 'KKH Amsterdamer Straße');
CREATE INDEX IF NOT EXISTS idx_baustellen_firma ON baustellen(firma_id);

-- Rechnungen
CREATE TABLE IF NOT EXISTS rechnungen (
    id               SERIAL PRIMARY KEY,
    firma_id         INTEGER NOT NULL REFERENCES firmen(id),
    baustelle_id     INTEGER REFERENCES baustellen(id) ON DELETE SET NULL,
    nummer           TEXT NOT NULL UNIQUE,         -- z. B. EX-2026-0001
    kunde_name       TEXT NOT NULL DEFAULT '',
    kunde_anrede     TEXT NOT NULL DEFAULT '',
    kunde_adresse    TEXT NOT NULL DEFAULT '',
    kunde_email      TEXT NOT NULL DEFAULT '',
    datum            TEXT NOT NULL DEFAULT '',     -- ISO-Datum
    leistungszeitraum TEXT NOT NULL DEFAULT '',
    zahlungsziel     INTEGER NOT NULL DEFAULT 30, -- Tage
    status           TEXT NOT NULL DEFAULT 'entwurf'
                     CHECK (status IN ('entwurf','versendet','bezahlt','storniert','ueberfaellig')),
    netto            NUMERIC(12,2) NOT NULL DEFAULT 0,
    ust_betrag       NUMERIC(12,2) NOT NULL DEFAULT 0,
    brutto           NUMERIC(12,2) NOT NULL DEFAULT 0,
    notiz            TEXT NOT NULL DEFAULT '',
    betreff          TEXT NOT NULL DEFAULT '',
    versendet_am     TIMESTAMPTZ,
    bezahlt_am       TIMESTAMPTZ,
    erstellt_am      TIMESTAMPTZ NOT NULL DEFAULT now(),
    geaendert_am     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rechnungen_firma ON rechnungen(firma_id);
CREATE INDEX IF NOT EXISTS idx_rechnungen_status ON rechnungen(status);
CREATE INDEX IF NOT EXISTS idx_rechnungen_datum ON rechnungen(datum DESC);

CREATE TABLE IF NOT EXISTS rechnung_positionen (
    id           SERIAL PRIMARY KEY,
    rechnung_id  INTEGER NOT NULL REFERENCES rechnungen(id) ON DELETE CASCADE,
    pos          INTEGER NOT NULL DEFAULT 1,
    bezeichnung  TEXT NOT NULL,
    menge        NUMERIC(10,2) NOT NULL DEFAULT 1,
    einheit      TEXT NOT NULL DEFAULT 'Stk.',
    einzelpreis  NUMERIC(10,2) NOT NULL DEFAULT 0,
    betrag       NUMERIC(12,2) NOT NULL DEFAULT 0   -- menge * einzelpreis
);
CREATE INDEX IF NOT EXISTS idx_rechnung_pos ON rechnung_positionen(rechnung_id, pos);

-- Ausgaben / Kosten
CREATE TABLE IF NOT EXISTS ausgaben (
    id           SERIAL PRIMARY KEY,
    firma_id     INTEGER REFERENCES firmen(id) ON DELETE SET NULL,
    baustelle_id INTEGER REFERENCES baustellen(id) ON DELETE SET NULL,
    datum        TEXT NOT NULL,
    kategorie    TEXT NOT NULL DEFAULT '',        -- Material, Fahrt, Werkzeug, etc.
    bezeichnung  TEXT NOT NULL,
    betrag       NUMERIC(12,2) NOT NULL,
    beleg_notiz  TEXT NOT NULL DEFAULT '',
    erstellt_am  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ausgaben_firma ON ausgaben(firma_id);
CREATE INDEX IF NOT EXISTS idx_ausgaben_datum ON ausgaben(datum DESC);

-- Arbeitszeiterfassung
CREATE TABLE IF NOT EXISTS zeiterfassung (
    id           SERIAL PRIMARY KEY,
    mitarbeiter  TEXT NOT NULL,
    datum        TEXT NOT NULL,
    von          TEXT NOT NULL DEFAULT '',        -- HH:MM
    bis          TEXT NOT NULL DEFAULT '',        -- HH:MM
    pause_min    INTEGER NOT NULL DEFAULT 0,
    baustelle_id INTEGER REFERENCES baustellen(id) ON DELETE SET NULL,
    taetigkeit   TEXT NOT NULL DEFAULT '',
    bemerkung    TEXT NOT NULL DEFAULT '',
    erstellt_am  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_zeiterfassung_ma ON zeiterfassung(mitarbeiter, datum DESC);
CREATE INDEX IF NOT EXISTS idx_zeiterfassung_datum ON zeiterfassung(datum DESC);

-- =====================================================================
-- KI-Fotoerkennung (Service unter server/ki/, gleiche Tabellen dort
-- ebenfalls idempotent angelegt – Reihenfolge des Starts ist damit egal)
-- =====================================================================

-- Analyse-Warteschlange + Ergebnisse je Foto
CREATE TABLE IF NOT EXISTS foto_analysen (
    id             SERIAL PRIMARY KEY,
    pfad           TEXT UNIQUE NOT NULL,          -- relativer Pfad im Dateispeicher
    room_id        TEXT NOT NULL DEFAULT '',
    bildtyp        TEXT NOT NULL DEFAULT '',      -- menue / geraet / uebersicht
    felder         JSONB NOT NULL DEFAULT '{}',   -- erkannte Werte + Konfidenz + OCR-Rohtext
    abgleich       JSONB NOT NULL DEFAULT '{}',   -- erkannt vs. Stammdaten je Feld
    status         TEXT NOT NULL DEFAULT 'wartet'
                   CHECK (status IN ('wartet','laeuft','uebereinstimmung','abweichung','unlesbar','fehler')),
    modell_version TEXT NOT NULL DEFAULT '',
    fehler         TEXT NOT NULL DEFAULT '',
    erstellt_am    TIMESTAMPTZ NOT NULL DEFAULT now(),
    analysiert_am  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_foto_analysen_status ON foto_analysen(status);
CREATE INDEX IF NOT EXISTS idx_foto_analysen_room ON foto_analysen(room_id);

-- Bestätigte Wahrheit je Foto = Trainingsdaten der eigenen Netze
CREATE TABLE IF NOT EXISTS ki_labels (
    id          SERIAL PRIMARY KEY,
    pfad        TEXT NOT NULL,
    feld        TEXT NOT NULL,                    -- bildtyp / seriennummer / freenet_id / tv_typ / gueltig_bis
    wert        TEXT NOT NULL,
    quelle      TEXT NOT NULL DEFAULT 'web',      -- web (Monteur) / auto (Selbst-Matching)
    erstellt_am TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (pfad, feld)
);

-- Modell-Versionen mit Validierungs-Genauigkeit
CREATE TABLE IF NOT EXISTS ki_modelle (
    id            SERIAL PRIMARY KEY,
    name          TEXT NOT NULL,                  -- klassifikator / feldnetz
    version       TEXT NOT NULL,
    genauigkeit   REAL,
    anzahl_labels INTEGER NOT NULL DEFAULT 0,
    trainiert_am  TIMESTAMPTZ NOT NULL DEFAULT now()
);
