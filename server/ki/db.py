"""Datenbankzugriff des KI-Service (PostgreSQL, gleiche DB wie das Backend).

Die KI-Tabellen werden hier zusätzlich idempotent angelegt, damit der
KI-Container auch dann startet, wenn das Backend das Schema noch nicht
angewendet hat (CREATE TABLE IF NOT EXISTS ist gefahrlos doppelt).
"""

import os
import json
import time

import psycopg2
import psycopg2.extras

_DSN = {
    "host": os.environ.get("PGHOST", "db"),
    "user": os.environ.get("PGUSER", "kkh"),
    "password": os.environ.get("PGPASSWORD", ""),
    "dbname": os.environ.get("PGDATABASE", "kkh"),
}

SCHEMA = """
CREATE TABLE IF NOT EXISTS foto_analysen (
    id             SERIAL PRIMARY KEY,
    pfad           TEXT UNIQUE NOT NULL,
    room_id        TEXT NOT NULL DEFAULT '',
    bildtyp        TEXT NOT NULL DEFAULT '',
    felder         JSONB NOT NULL DEFAULT '{}',
    abgleich       JSONB NOT NULL DEFAULT '{}',
    status         TEXT NOT NULL DEFAULT 'wartet'
                   CHECK (status IN ('wartet','laeuft','uebereinstimmung','abweichung','unlesbar','fehler')),
    modell_version TEXT NOT NULL DEFAULT '',
    fehler         TEXT NOT NULL DEFAULT '',
    erstellt_am    TIMESTAMPTZ NOT NULL DEFAULT now(),
    analysiert_am  TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_foto_analysen_status ON foto_analysen(status);
CREATE INDEX IF NOT EXISTS idx_foto_analysen_room ON foto_analysen(room_id);

CREATE TABLE IF NOT EXISTS ki_labels (
    id          SERIAL PRIMARY KEY,
    pfad        TEXT NOT NULL,
    feld        TEXT NOT NULL,           -- bildtyp / seriennummer / freenet_id / tv_typ / gueltig_bis
    wert        TEXT NOT NULL,
    quelle      TEXT NOT NULL DEFAULT 'web',  -- web (Monteur bestätigt) / auto (Selbst-Matching)
    erstellt_am TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (pfad, feld)
);

CREATE TABLE IF NOT EXISTS ki_modelle (
    id            SERIAL PRIMARY KEY,
    name          TEXT NOT NULL,          -- klassifikator / feldnetz
    version       TEXT NOT NULL,
    genauigkeit   REAL,
    anzahl_labels INTEGER NOT NULL DEFAULT 0,
    trainiert_am  TIMESTAMPTZ NOT NULL DEFAULT now()
);
"""


def connect(retries: int = 30):
    """Verbindung mit Wartezeit – die DB kann beim Stack-Start noch hochfahren."""
    last = None
    for _ in range(retries):
        try:
            conn = psycopg2.connect(**_DSN)
            conn.autocommit = True
            return conn
        except psycopg2.OperationalError as e:
            last = e
            time.sleep(2)
    raise last


def init_schema():
    with connect() as conn, conn.cursor() as cur:
        cur.execute(SCHEMA)


def query(sql, params=None):
    with connect() as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, params or ())
            if cur.description:
                return cur.fetchall()
            return []


def execute(sql, params=None):
    with connect() as conn, conn.cursor() as cur:
        cur.execute(sql, params or ())
        return cur.rowcount


def hole_naechsten_job():
    """Nächstes wartendes Foto atomar reservieren (FOR UPDATE SKIP LOCKED)."""
    with connect() as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(
                """UPDATE foto_analysen SET status='laeuft'
                   WHERE id = (SELECT id FROM foto_analysen WHERE status='wartet'
                               ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED)
                   RETURNING id, pfad, room_id"""
            )
            return cur.fetchone()


def speichere_ergebnis(analyse_id, bildtyp, felder, abgleich, status, modell_version, fehler=""):
    execute(
        """UPDATE foto_analysen
           SET bildtyp=%s, felder=%s, abgleich=%s, status=%s,
               modell_version=%s, fehler=%s, analysiert_am=now()
           WHERE id=%s""",
        (bildtyp, json.dumps(felder), json.dumps(abgleich), status,
         modell_version, fehler, analyse_id),
    )


def hole_zimmer(room_id):
    rows = query("SELECT * FROM rooms WHERE id=%s", (room_id,))
    return rows[0] if rows else None


def setze_auto_label(pfad, feld, wert):
    """Auto-Label nur setzen, wenn noch kein (manuelles) Label existiert."""
    execute(
        """INSERT INTO ki_labels (pfad, feld, wert, quelle) VALUES (%s,%s,%s,'auto')
           ON CONFLICT (pfad, feld) DO NOTHING""",
        (pfad, feld, wert),
    )
