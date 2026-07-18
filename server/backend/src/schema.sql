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
