-- KKH TV-Wartung – Serverdatenbank (PostgreSQL)
-- Phase 1: Zimmer/Stationen, Prüfbögen, Stundenzettel, Dateien-Metadaten.
-- (Das Schema ist bewusst so angelegt, dass später Lager/Artikel/Baustellen
--  als weitere Tabellen ergänzt werden können.)

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
