/**
 * KKH TV-Wartung – Server (Sync-API für die Android-App + Weboberfläche).
 * Läuft unter BASE_PATH (Standard /kkh) hinter dem vorhandenen Reverse-Proxy.
 */
const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { pool, init } = require('./db');

const BASE_PATH = (process.env.BASE_PATH || '/kkh').replace(/\/$/, '');
const PORT = Number(process.env.PORT || 8090);
const API_KEY = process.env.KKH_API_KEY || '';
const ADMIN_PASSWORD = process.env.KKH_ADMIN_PASSWORD || '';
const SECRET = process.env.KKH_SECRET || crypto.randomBytes(32).toString('hex');
const FILES_DIR = process.env.FILES_DIR || '/data/files';

if (!API_KEY || !ADMIN_PASSWORD) {
  console.error('KKH_API_KEY und KKH_ADMIN_PASSWORD müssen gesetzt sein (.env).');
  process.exit(1);
}
fs.mkdirSync(FILES_DIR, { recursive: true });

const app = express();
app.set('trust proxy', true);
const router = express.Router();

// ---------- Hilfsfunktionen ----------

const nowIso = () => new Date().toISOString().slice(0, 19);

function signSession(expiresMs) {
  const payload = String(expiresMs);
  const mac = crypto.createHmac('sha256', SECRET).update(payload).digest('hex');
  return `${payload}.${mac}`;
}

function checkSession(token) {
  if (!token) return false;
  const [payload, mac] = token.split('.');
  if (!payload || !mac) return false;
  const expected = crypto.createHmac('sha256', SECRET).update(payload).digest('hex');
  try {
    if (!crypto.timingSafeEqual(Buffer.from(mac), Buffer.from(expected))) return false;
  } catch {
    return false;
  }
  return Number(payload) > Date.now();
}

function parseCookies(req) {
  const raw = req.headers.cookie || '';
  return Object.fromEntries(
    raw.split(';').map((c) => c.trim().split('=').map(decodeURIComponent)).filter((p) => p[0])
  );
}

// App-Sync: fester API-Schlüssel
function requireApiKey(req, res, next) {
  const key = req.get('X-Api-Key') || '';
  try {
    if (key.length === API_KEY.length &&
        crypto.timingSafeEqual(Buffer.from(key), Buffer.from(API_KEY))) return next();
  } catch { /* Längen ungleich */ }
  res.status(401).json({ error: 'Ungültiger API-Schlüssel' });
}

// Weboberfläche: Session-Cookie
function requireWebAuth(req, res, next) {
  if (checkSession(parseCookies(req).kkh_session)) return next();
  res.status(401).json({ error: 'Nicht angemeldet' });
}

// Pfade im Dateispeicher absichern (kein Ausbruch aus FILES_DIR)
function safeFilePath(rel) {
  const ziel = path.normalize(path.join(FILES_DIR, rel));
  if (!ziel.startsWith(path.normalize(FILES_DIR) + path.sep)) return null;
  return ziel;
}

function listFiles(dir, base) {
  const ergebnis = [];
  if (!fs.existsSync(dir)) return ergebnis;
  for (const eintrag of fs.readdirSync(dir, { withFileTypes: true })) {
    const voll = path.join(dir, eintrag.name);
    if (eintrag.isDirectory()) ergebnis.push(...listFiles(voll, base));
    else {
      const st = fs.statSync(voll);
      ergebnis.push({ path: path.relative(base, voll).split(path.sep).join('/'), size: st.size });
    }
  }
  return ergebnis;
}

const roomToJson = (r) => ({
  id: r.id, station: r.station, zimmer: r.zimmer, lebenslauf: r.lebenslauf,
  letztePruefung: r.letzte_pruefung, tvTyp: r.tv_typ, seriennummer: r.seriennummer,
  freenetId: r.freenet_id, gueltigBis: r.gueltig_bis, inaktiv: r.inaktiv,
  updatedAt: r.updated_at,
});

async function upsertRoomLww(r) {
  // Nur übernehmen, wenn neuer als der vorhandene Stand (last-write-wins)
  const res = await pool.query(
    `INSERT INTO rooms (id, station, zimmer, lebenslauf, letzte_pruefung, tv_typ,
                        seriennummer, freenet_id, gueltig_bis, inaktiv, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
     ON CONFLICT (id) DO UPDATE SET
       station=EXCLUDED.station, zimmer=EXCLUDED.zimmer, lebenslauf=EXCLUDED.lebenslauf,
       letzte_pruefung=EXCLUDED.letzte_pruefung, tv_typ=EXCLUDED.tv_typ,
       seriennummer=EXCLUDED.seriennummer, freenet_id=EXCLUDED.freenet_id,
       gueltig_bis=EXCLUDED.gueltig_bis, inaktiv=EXCLUDED.inaktiv,
       updated_at=EXCLUDED.updated_at
     WHERE rooms.updated_at < EXCLUDED.updated_at
     RETURNING id`,
    [r.id, r.station || '', r.zimmer || '', r.lebenslauf || '', r.letztePruefung || '',
     r.tvTyp || '', r.seriennummer || '', r.freenetId || '', r.gueltigBis || '',
     !!r.inaktiv, r.updatedAt || '']
  );
  return res.rowCount > 0;
}

// ---------- App-Sync-API ----------

router.get('/api/sync/rooms', requireApiKey, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM rooms');
  res.json({ rooms: rows.map(roomToJson) });
});

router.post('/api/sync/rooms', requireApiKey, express.json({ limit: '50mb' }), async (req, res) => {
  let uebernommen = 0;
  for (const r of req.body.rooms || []) {
    if (!r.id) continue;
    if (await upsertRoomLww(r)) uebernommen++;
  }
  res.json({ uebernommen });
});

router.post('/api/sync/inspections', requireApiKey, express.json({ limit: '50mb' }), async (req, res) => {
  let neu = 0;
  for (const i of req.body.inspections || []) {
    if (!i.uuid || !i.roomId) continue;
    const daten = { punkte: i.punkte || [], arbeiten: i.arbeiten || [], bemerkungen: i.bemerkungen || '' };
    const r = await pool.query(
      `INSERT INTO inspections (uuid, room_id, datum, daten, mitarbeiter, geloescht)
       VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (uuid) DO UPDATE SET geloescht = EXCLUDED.geloescht
       RETURNING (xmax = 0) AS inserted`,
      [i.uuid, i.roomId, i.datum || '', JSON.stringify(daten), i.mitarbeiter || '', !!i.geloescht]
    );
    if (r.rows[0] && r.rows[0].inserted) neu++;
  }
  res.json({ neu });
});

router.post('/api/sync/stundenzettel', requireApiKey, express.json({ limit: '10mb' }), async (req, res) => {
  let uebernommen = 0;
  for (const z of req.body.zettel || []) {
    if (!z.station || !z.zeitraumStart) continue;
    const r = await pool.query(
      `INSERT INTO stundenzettel (station, zeitraum_start, auftragsnummer, datum,
                                  stunden, anfahrt, techniker, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
       ON CONFLICT (station, zeitraum_start) DO UPDATE SET
         auftragsnummer=EXCLUDED.auftragsnummer, datum=EXCLUDED.datum,
         stunden=EXCLUDED.stunden, anfahrt=EXCLUDED.anfahrt,
         techniker=EXCLUDED.techniker, updated_at=EXCLUDED.updated_at
       WHERE stundenzettel.updated_at < EXCLUDED.updated_at
       RETURNING station`,
      [z.station, z.zeitraumStart, z.auftragsnummer || '', z.datum || '',
       z.stunden || '', z.anfahrt || '', z.techniker || '', z.updatedAt || '']
    );
    if (r.rowCount > 0) uebernommen++;
  }
  res.json({ uebernommen });
});

router.get('/api/sync/files', requireApiKey, (req, res) => {
  res.json({ files: listFiles(FILES_DIR, FILES_DIR) });
});

router.put('/api/sync/file', requireApiKey,
  express.raw({ type: () => true, limit: '300mb' }), (req, res) => {
    const ziel = safeFilePath(String(req.query.path || ''));
    if (!ziel || !req.query.path) return res.status(400).json({ error: 'Ungültiger Pfad' });
    fs.mkdirSync(path.dirname(ziel), { recursive: true });
    fs.writeFileSync(ziel, req.body);
    res.json({ ok: true });
  });

// ---------- Web-API (Session) ----------

router.post('/api/login', express.json(), (req, res) => {
  const pw = String(req.body.password || '');
  const ok = pw.length === ADMIN_PASSWORD.length &&
    crypto.timingSafeEqual(Buffer.from(pw), Buffer.from(ADMIN_PASSWORD.padEnd(pw.length)));
  if (!ok) return res.status(401).json({ error: 'Falsches Passwort' });
  const token = signSession(Date.now() + 12 * 60 * 60 * 1000); // 12 Stunden
  res.setHeader('Set-Cookie',
    `kkh_session=${encodeURIComponent(token)}; Path=${BASE_PATH || '/'}; HttpOnly; SameSite=Lax; Max-Age=43200`);
  res.json({ ok: true });
});

router.post('/api/logout', (req, res) => {
  res.setHeader('Set-Cookie', `kkh_session=; Path=${BASE_PATH || '/'}; HttpOnly; Max-Age=0`);
  res.json({ ok: true });
});

router.get('/api/web/me', requireWebAuth, (req, res) => res.json({ ok: true }));

router.get('/api/web/overview', requireWebAuth, async (req, res) => {
  const rooms = (await pool.query('SELECT * FROM rooms')).rows;
  const heute = new Date().toISOString().slice(0, 10);
  const tage7 = new Date(Date.now() - 7 * 86400e3).toISOString().slice(0, 10);
  const tage30 = new Date(Date.now() - 30 * 86400e3).toISOString().slice(0, 10);
  const monat3 = new Date(Date.now() + 92 * 86400e3).toISOString().slice(0, 10);
  const aktiv = rooms.filter((r) => !r.inaktiv);
  const inspAgg = (await pool.query(
    `SELECT count(*) FILTER (WHERE datum >= $1) AS d7,
            count(*) FILTER (WHERE datum >= $2) AS d30, count(*) AS gesamt
     FROM inspections`, [tage7, tage30])).rows[0];
  const letzte = (await pool.query(
    `SELECT i.uuid, i.room_id, i.datum, i.daten, r.station, r.zimmer
     FROM inspections i LEFT JOIN rooms r ON r.id = i.room_id
     ORDER BY i.datum DESC, i.created_at DESC LIMIT 15`)).rows;
  res.json({
    zimmerGesamt: rooms.length,
    zimmerAktiv: aktiv.length,
    freenetAbgelaufen: aktiv.filter((r) => r.gueltig_bis && r.gueltig_bis < heute).length,
    freenetBald: aktiv.filter((r) => r.gueltig_bis && r.gueltig_bis >= heute && r.gueltig_bis <= monat3).length,
    pruefungen7: Number(inspAgg.d7), pruefungen30: Number(inspAgg.d30),
    pruefungenGesamt: Number(inspAgg.gesamt),
    letztePruefungen: letzte,
  });
});

router.get('/api/web/rooms', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM rooms ORDER BY station, zimmer');
  res.json({ rooms: rows.map(roomToJson) });
});

router.post('/api/web/rooms', requireWebAuth, express.json(), async (req, res) => {
  const b = req.body || {};
  const station = String(b.station || '').trim();
  const zimmer = String(b.zimmer || '').trim();
  if (!station || !zimmer) return res.status(400).json({ error: 'Station und Zimmer angeben' });
  const id = `${station}_${zimmer}`;
  const existiert = await pool.query('SELECT 1 FROM rooms WHERE id=$1', [id]);
  if (existiert.rowCount > 0) return res.status(409).json({ error: `${id} existiert bereits` });
  const heute = new Date().toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
  await upsertRoomLww({
    id, station, zimmer,
    lebenslauf: `${heute}: Zimmer über die Weboberfläche angelegt`,
    tvTyp: b.tvTyp || '', seriennummer: b.seriennummer || '',
    freenetId: b.freenetId || '', gueltigBis: b.gueltigBis || '',
    inaktiv: false, updatedAt: nowIso(),
  });
  res.json({ ok: true, id });
});

router.patch('/api/web/rooms/:id', requireWebAuth, express.json(), async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM rooms WHERE id=$1', [req.params.id]);
  if (rows.length === 0) return res.status(404).json({ error: 'Zimmer nicht gefunden' });
  const alt = roomToJson(rows[0]);
  const b = req.body || {};
  let lebenslauf = b.lebenslauf !== undefined ? b.lebenslauf : alt.lebenslauf;
  if (b.inaktiv !== undefined && b.inaktiv !== alt.inaktiv) {
    const heute = new Date().toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
    const vermerk = b.inaktiv
      ? `${heute}: Zimmer inaktiv gesetzt (Weboberfläche)`
      : `${heute}: Zimmer reaktiviert (Weboberfläche)`;
    lebenslauf = lebenslauf ? `${lebenslauf.trimEnd()}\n${vermerk}` : vermerk;
  }
  await upsertRoomLww({
    ...alt,
    tvTyp: b.tvTyp !== undefined ? b.tvTyp : alt.tvTyp,
    seriennummer: b.seriennummer !== undefined ? b.seriennummer : alt.seriennummer,
    freenetId: b.freenetId !== undefined ? b.freenetId : alt.freenetId,
    gueltigBis: b.gueltigBis !== undefined ? b.gueltigBis : alt.gueltigBis,
    inaktiv: b.inaktiv !== undefined ? !!b.inaktiv : alt.inaktiv,
    lebenslauf,
    updatedAt: nowIso(),
  });
  res.json({ ok: true });
});

router.get('/api/web/rooms/:id', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM rooms WHERE id=$1', [req.params.id]);
  if (rows.length === 0) return res.status(404).json({ error: 'Zimmer nicht gefunden' });
  const inspections = (await pool.query(
    'SELECT uuid, datum, daten FROM inspections WHERE room_id=$1 ORDER BY datum DESC', [req.params.id])).rows;
  const dateien = listFiles(path.join(FILES_DIR, req.params.id), FILES_DIR);
  res.json({ room: roomToJson(rows[0]), inspections, files: dateien });
});

router.get('/api/web/inspections', requireWebAuth, async (req, res) => {
  const limit = Math.min(Number(req.query.limit || 300), 1000);
  const { rows } = await pool.query(
    `SELECT i.uuid, i.room_id, i.datum, i.daten, r.station, r.zimmer
     FROM inspections i LEFT JOIN rooms r ON r.id = i.room_id
     ORDER BY i.datum DESC, i.created_at DESC LIMIT $1`, [limit]);
  res.json({ inspections: rows });
});

router.get('/api/web/stundenzettel', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query(
    'SELECT * FROM stundenzettel ORDER BY zeitraum_start DESC, station');
  res.json({ zettel: rows });
});

router.get('/api/web/file', requireWebAuth, (req, res) => {
  const ziel = safeFilePath(String(req.query.path || ''));
  if (!ziel || !fs.existsSync(ziel)) return res.status(404).json({ error: 'Datei nicht gefunden' });
  res.sendFile(ziel);
});


// ---------- Voll-Synchronisation (Spiegel der App-Daten, Replace-All) ----------

async function replaceAll(tabelle, spalten, zeilen) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query(`DELETE FROM ${tabelle}`);
    for (const z of zeilen) {
      const platzhalter = spalten.map((_, i) => `$${i + 1}`).join(',');
      await client.query(
        `INSERT INTO ${tabelle} (${spalten.join(',')}) VALUES (${platzhalter})`,
        spalten.map((sp) => z[sp]));
    }
    await client.query('COMMIT');
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}

router.post('/api/sync/sperren', requireApiKey, express.json({ limit: '5mb' }), async (req, res) => {
  await replaceAll('sperren', ['room_id', 'gesperrt_am', 'grund'],
    (req.body.sperren || []).map((s) => ({
      room_id: s.roomId, gesperrt_am: s.gesperrtAm || '', grund: s.grund || '' })));
  res.json({ ok: true });
});

router.post('/api/sync/material', requireApiKey, express.json({ limit: '5mb' }), async (req, res) => {
  await replaceAll('material', ['name', 'bestand', 'bestand_aktiv', 'aktiv', 'sort_index'],
    (req.body.material || []).map((m) => ({
      name: m.name, bestand: m.bestand || 0, bestand_aktiv: !!m.bestandAktiv,
      aktiv: m.aktiv !== false, sort_index: m.sortIndex || 0 })));
  res.json({ ok: true });
});

router.post('/api/sync/pruefpunkte', requireApiKey, express.json({ limit: '5mb' }), async (req, res) => {
  await replaceAll('app_pruefpunkte', ['titel', 'aktiv', 'sort_index'],
    (req.body.punkte || []).map((p) => ({
      titel: p.titel, aktiv: p.aktiv !== false, sort_index: p.sortIndex || 0 })));
  res.json({ ok: true });
});

router.post('/api/sync/aktivitaet', requireApiKey, express.json({ limit: '50mb' }), async (req, res) => {
  await replaceAll('app_aktivitaet', ['room_id', 'zeitpunkt', 'aktion'],
    (req.body.eintraege || []).map((a) => ({
      room_id: a.roomId, zeitpunkt: a.zeitpunkt || '', aktion: a.aktion || '' })));
  res.json({ ok: true });
});

// Lesend für die Weboberfläche (Einbindung optional)
router.get('/api/web/material', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM material ORDER BY sort_index, name');
  res.json({ material: rows });
});
router.get('/api/web/sperren', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM sperren ORDER BY room_id');
  res.json({ sperren: rows });
});
router.get('/api/web/aktivitaet', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM app_aktivitaet ORDER BY zeitpunkt DESC LIMIT 500');
  res.json({ aktivitaet: rows });
});


// ---------- v1.9: Mitarbeiter, Kollegen-Pull, Team-Stundenzettel ----------

router.get('/api/sync/mitarbeiter', requireApiKey, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM mitarbeiter ORDER BY name');
  res.json({ mitarbeiter: rows });
});

// Delta-Pull: alle Prüfbögen (aller Geräte), optional nur neue seit ?since=
router.get('/api/sync/inspections', requireApiKey, async (req, res) => {
  const since = String(req.query.since || '');
  const { rows } = since
    ? await pool.query(
        `SELECT uuid, room_id, datum, daten, mitarbeiter, geloescht FROM inspections
         WHERE created_at > $1::timestamptz ORDER BY created_at`, [since])
    : await pool.query(
        'SELECT uuid, room_id, datum, daten, mitarbeiter, geloescht FROM inspections ORDER BY created_at');
  res.json({
    inspections: rows.map((r) => ({
      uuid: r.uuid, roomId: r.room_id, datum: r.datum,
      punkte: (r.daten || {}).punkte || [], arbeiten: (r.daten || {}).arbeiten || [],
      bemerkungen: (r.daten || {}).bemerkungen || '',
      mitarbeiter: r.mitarbeiter, geloescht: r.geloescht,
    })),
  });
});

router.get('/api/sync/zettel-eintraege', requireApiKey, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM zettel_eintraege');
  res.json({ eintraege: rows.map((e) => ({
    station: e.station, zeitraumStart: e.zeitraum_start, mitarbeiter: e.mitarbeiter,
    stunden: e.stunden, anfahrt: e.anfahrt, updatedAt: e.updated_at })) });
});

router.post('/api/sync/zettel-eintraege', requireApiKey, express.json({ limit: '5mb' }), async (req, res) => {
  let uebernommen = 0;
  for (const e of req.body.eintraege || []) {
    if (!e.station || !e.zeitraumStart || !e.mitarbeiter) continue;
    const r = await pool.query(
      `INSERT INTO zettel_eintraege (station, zeitraum_start, mitarbeiter, stunden, anfahrt, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (station, zeitraum_start, mitarbeiter) DO UPDATE SET
         stunden=EXCLUDED.stunden, anfahrt=EXCLUDED.anfahrt, updated_at=EXCLUDED.updated_at
       WHERE zettel_eintraege.updated_at < EXCLUDED.updated_at
       RETURNING station`,
      [e.station, e.zeitraumStart, e.mitarbeiter, e.stunden || '', e.anfahrt || '', e.updatedAt || '']);
    if (r.rowCount > 0) uebernommen++;
  }
  res.json({ uebernommen });
});

// Mitarbeiter-Verwaltung (Web)
router.get('/api/web/mitarbeiter', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM mitarbeiter ORDER BY name');
  res.json({ mitarbeiter: rows });
});
router.post('/api/web/mitarbeiter', requireWebAuth, express.json(), async (req, res) => {
  const name = String(req.body.name || '').trim();
  if (!name) return res.status(400).json({ error: 'Name angeben' });
  await pool.query(
    'INSERT INTO mitarbeiter (name) VALUES ($1) ON CONFLICT (name) DO UPDATE SET aktiv = TRUE', [name]);
  res.json({ ok: true });
});
router.patch('/api/web/mitarbeiter/:name', requireWebAuth, express.json(), async (req, res) => {
  await pool.query('UPDATE mitarbeiter SET aktiv=$1 WHERE name=$2',
    [req.body.aktiv !== false, req.params.name]);
  res.json({ ok: true });
});

router.get('/api/web/zettel-eintraege', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM zettel_eintraege ORDER BY zeitraum_start DESC, station');
  res.json({ eintraege: rows });
});

// ---------- App-Updater ----------
// public/app/ enthält die aktuelle APK + version.json (per Deploy/Commit gepflegt)
router.get('/api/app/version', (req, res) => {
  const f = path.join(__dirname, '..', 'public', 'app', 'version.json');
  if (!fs.existsSync(f)) return res.status(404).json({ error: 'Keine Version hinterlegt' });
  res.sendFile(f);
});

// ---------- Thumbnails (Server-Power: Web-Galerie lädt Vorschauen) ----------
let sharp = null;
try { sharp = require('sharp'); } catch { console.log('sharp nicht verfügbar – Thumbs deaktiviert'); }
const THUMB_DIR = path.join(FILES_DIR, '..', 'thumbs');
fs.mkdirSync(THUMB_DIR, { recursive: true });

router.get('/api/web/thumb', requireWebAuth, async (req, res) => {
  const rel = String(req.query.path || '');
  const quelle = safeFilePath(rel);
  if (!quelle || !fs.existsSync(quelle)) return res.status(404).json({ error: 'Datei nicht gefunden' });
  if (!sharp || !/\.(jpe?g|png)$/i.test(rel)) return res.sendFile(quelle);
  const ziel = path.join(THUMB_DIR, crypto.createHash('sha1').update(rel).digest('hex') + '.jpg');
  if (!fs.existsSync(ziel)) {
    try {
      await sharp(quelle).rotate().resize(360, 360, { fit: 'cover' }).jpeg({ quality: 70 }).toFile(ziel);
    } catch { return res.sendFile(quelle); }
  }
  res.sendFile(ziel);
});

// ---------- Automatisches nächtliches Backup (pg_dump, 30 Tage Rotation) ----------
const BACKUP_DIR = path.join(FILES_DIR, '..', 'backups');
fs.mkdirSync(BACKUP_DIR, { recursive: true });
function nightlyBackup() {
  const { execFile } = require('child_process');
  const datum = new Date().toISOString().slice(0, 10);
  const ziel = path.join(BACKUP_DIR, `kkh-db-${datum}.sql.gz`);
  const env = { ...process.env, PGPASSWORD: process.env.PGPASSWORD };
  const dump = execFile('sh', ['-c',
    `pg_dump -h ${process.env.PGHOST || 'db'} -U ${process.env.PGUSER || 'kkh'} ${process.env.PGDATABASE || 'kkh'} | gzip > ${JSON.stringify(ziel)}`],
    { env }, (err) => {
      if (err) console.error('Backup fehlgeschlagen:', err.message);
      else console.log('Backup erstellt:', ziel);
      // Rotation: älter als 30 Tage löschen
      const limit = Date.now() - 30 * 86400e3;
      for (const f of fs.readdirSync(BACKUP_DIR)) {
        const voll = path.join(BACKUP_DIR, f);
        if (fs.statSync(voll).mtimeMs < limit) fs.unlinkSync(voll);
      }
    });
}
setInterval(nightlyBackup, 24 * 60 * 60 * 1000);
setTimeout(nightlyBackup, 60 * 1000);

// ---------- Statische Weboberfläche ----------

router.use(express.static(path.join(__dirname, '..', 'public')));

app.use(BASE_PATH || '/', router);
if (BASE_PATH) app.get(BASE_PATH, (req, res) => res.redirect(`${BASE_PATH}/`));
app.get('/', (req, res) => res.redirect(`${BASE_PATH}/`));

init().then(() => {
  app.listen(PORT, () => console.log(`KKH-Server läuft auf Port ${PORT} unter ${BASE_PATH}/`));
}).catch((e) => {
  console.error('Start fehlgeschlagen:', e);
  process.exit(1);
});
