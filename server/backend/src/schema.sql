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
