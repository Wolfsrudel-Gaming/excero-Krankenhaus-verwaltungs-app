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

if (!API_KEY) {
  console.error('KKH_API_KEY muss gesetzt sein (.env).');
  process.exit(1);
}
// ADMIN_PASSWORD wird nicht mehr für den Login verwendet (Mehrbenutzer über
// die users-Tabelle), bleibt aber für die Abwärtskompatibilität akzeptiert.
void ADMIN_PASSWORD;
fs.mkdirSync(FILES_DIR, { recursive: true });

const app = express();
app.set('trust proxy', true);
const router = express.Router();

// ---------- Hilfsfunktionen ----------

const nowIso = () => new Date().toISOString().slice(0, 19);

// ---------- Passwort-Hashing (scrypt, ohne externe Abhängigkeiten) ----------

function hashPassword(passwort) {
  const salt = crypto.randomBytes(16).toString('hex');
  const hash = crypto.scryptSync(String(passwort), salt, 64).toString('hex');
  return { hash, salt };
}

function verifyPassword(passwort, hash, salt) {
  if (!hash || !salt) return false;
  const kandidat = crypto.scryptSync(String(passwort), salt, 64);
  const erwartet = Buffer.from(hash, 'hex');
  if (kandidat.length !== erwartet.length) return false;
  return crypto.timingSafeEqual(kandidat, erwartet);
}

// ---------- Session (signiertes Cookie mit Benutzeridentität) ----------

function hmac(payload) {
  return crypto.createHmac('sha256', SECRET).update(payload).digest('hex');
}

function signSession(username, expiresMs) {
  const payload = Buffer.from(JSON.stringify({ u: username, e: expiresMs }))
    .toString('base64url');
  return `${payload}.${hmac(payload)}`;
}

// Gibt bei gültiger Session den Benutzernamen zurück, sonst null.
function checkSession(token) {
  if (!token) return null;
  const [payload, mac] = token.split('.');
  if (!payload || !mac) return null;
  const expected = hmac(payload);
  try {
    if (!crypto.timingSafeEqual(Buffer.from(mac), Buffer.from(expected))) return null;
  } catch {
    return null;
  }
  try {
    const { u, e } = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    if (!e || Number(e) <= Date.now()) return null;
    return u || null;
  } catch {
    return null;
  }
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
  const username = checkSession(parseCookies(req).kkh_session);
  if (username) { req.username = username; return next(); }
  res.status(401).json({ error: 'Nicht angemeldet' });
}

// ---------- Benutzer-Hilfsfunktionen ----------

async function findUser(username) {
  const { rows } = await pool.query(
    'SELECT * FROM users WHERE lower(username) = lower($1)', [String(username || '')]);
  return rows[0] || null;
}

async function countUsers() {
  return Number((await pool.query('SELECT count(*) AS n FROM users')).rows[0].n);
}

async function createUser(username, passwort) {
  const name = String(username || '').trim();
  if (name.length < 2) throw new Error('Benutzername muss mindestens 2 Zeichen haben');
  if (String(passwort || '').length < 4) throw new Error('Passwort muss mindestens 4 Zeichen haben');
  if (await findUser(name)) throw new Error(`Benutzer „${name}" existiert bereits`);
  const { hash, salt } = hashPassword(passwort);
  const { rows } = await pool.query(
    `INSERT INTO users (username, password_hash, salt) VALUES ($1,$2,$3)
     RETURNING id, username, created_at, updated_at`, [name, hash, salt]);
  return rows[0];
}

// Erstellt bei leerer Benutzertabelle den ersten Benutzer (Alexander).
async function seedFirstUser() {
  if (await countUsers() > 0) return;
  await createUser('Alexander', '123434');
  console.log('Erster Benutzer angelegt: Alexander (bitte Passwort nach dem ersten Login ändern).');
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
      `INSERT INTO inspections (uuid, room_id, datum, daten)
       VALUES ($1,$2,$3,$4) ON CONFLICT (uuid) DO NOTHING RETURNING uuid`,
      [i.uuid, i.roomId, i.datum || '', JSON.stringify(daten)]
    );
    if (r.rowCount > 0) neu++;
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

router.post('/api/login', express.json(), async (req, res) => {
  const username = String(req.body.username || '').trim();
  const pw = String(req.body.password || '');
  const user = username ? await findUser(username) : null;
  // Immer scrypt ausführen (Timing) – auch bei unbekanntem Benutzer
  const ok = user
    ? verifyPassword(pw, user.password_hash, user.salt)
    : verifyPassword(pw, crypto.randomBytes(64).toString('hex'), 'x') && false;
  if (!user || !ok) return res.status(401).json({ error: 'Benutzername oder Passwort falsch' });
  const token = signSession(user.username, Date.now() + 12 * 60 * 60 * 1000); // 12 Stunden
  res.setHeader('Set-Cookie',
    `kkh_session=${encodeURIComponent(token)}; Path=${BASE_PATH || '/'}; HttpOnly; SameSite=Lax; Max-Age=43200`);
  res.json({ ok: true, username: user.username });
});

router.post('/api/logout', (req, res) => {
  res.setHeader('Set-Cookie', `kkh_session=; Path=${BASE_PATH || '/'}; HttpOnly; Max-Age=0`);
  res.json({ ok: true });
});

router.get('/api/web/me', requireWebAuth, (req, res) => res.json({ ok: true, username: req.username }));

// ---------- Benutzerverwaltung (alle angemeldeten Benutzer haben Vollzugriff) ----------

router.get('/api/web/users', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query(
    'SELECT id, username, created_at, updated_at FROM users ORDER BY lower(username)');
  res.json({ users: rows, aktuell: req.username });
});

router.post('/api/web/users', requireWebAuth, express.json(), async (req, res) => {
  try {
    const user = await createUser(req.body.username, req.body.password);
    res.json({ ok: true, user });
  } catch (e) {
    res.status(400).json({ error: e.message });
  }
});

// Passwort eines beliebigen Benutzers zurücksetzen / Benutzer umbenennen
router.patch('/api/web/users/:id', requireWebAuth, express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const { rows } = await pool.query('SELECT * FROM users WHERE id=$1', [id]);
  if (rows.length === 0) return res.status(404).json({ error: 'Benutzer nicht gefunden' });
  const felder = [];
  const werte = [];
  if (req.body.username !== undefined) {
    const name = String(req.body.username).trim();
    if (name.length < 2) return res.status(400).json({ error: 'Benutzername zu kurz' });
    const kollision = await findUser(name);
    if (kollision && kollision.id !== id) return res.status(409).json({ error: 'Benutzername bereits vergeben' });
    werte.push(name); felder.push(`username=$${werte.length}`);
  }
  if (req.body.password !== undefined) {
    if (String(req.body.password).length < 4) return res.status(400).json({ error: 'Passwort muss mindestens 4 Zeichen haben' });
    const { hash, salt } = hashPassword(req.body.password);
    werte.push(hash); felder.push(`password_hash=$${werte.length}`);
    werte.push(salt); felder.push(`salt=$${werte.length}`);
  }
  if (felder.length === 0) return res.status(400).json({ error: 'Nichts zu ändern' });
  werte.push(id);
  await pool.query(
    `UPDATE users SET ${felder.join(', ')}, updated_at=now() WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});

router.delete('/api/web/users/:id', requireWebAuth, async (req, res) => {
  const id = Number(req.params.id);
  const { rows } = await pool.query('SELECT username FROM users WHERE id=$1', [id]);
  if (rows.length === 0) return res.status(404).json({ error: 'Benutzer nicht gefunden' });
  if (rows[0].username.toLowerCase() === String(req.username).toLowerCase())
    return res.status(400).json({ error: 'Der eigene Benutzer kann nicht gelöscht werden' });
  if (await countUsers() <= 1)
    return res.status(400).json({ error: 'Der letzte Benutzer kann nicht gelöscht werden' });
  await pool.query('DELETE FROM users WHERE id=$1', [id]);
  res.json({ ok: true });
});

// Eigenes Passwort ändern (aktuelles Passwort erforderlich)
router.post('/api/web/change-password', requireWebAuth, express.json(), async (req, res) => {
  const user = await findUser(req.username);
  if (!user) return res.status(404).json({ error: 'Benutzer nicht gefunden' });
  if (!verifyPassword(String(req.body.aktuell || ''), user.password_hash, user.salt))
    return res.status(401).json({ error: 'Aktuelles Passwort ist falsch' });
  if (String(req.body.neu || '').length < 4)
    return res.status(400).json({ error: 'Neues Passwort muss mindestens 4 Zeichen haben' });
  const { hash, salt } = hashPassword(req.body.neu);
  await pool.query('UPDATE users SET password_hash=$1, salt=$2, updated_at=now() WHERE id=$3',
    [hash, salt, user.id]);
  res.json({ ok: true });
});

// Verbindungsdaten & Sync-Datenstand für die Android-App
router.get('/api/web/app-info', requireWebAuth, async (req, res) => {
  const proto = req.get('x-forwarded-proto') || req.protocol || 'https';
  const host = req.get('host') || '';
  const agg = (await pool.query(`SELECT
      (SELECT count(*) FROM rooms)                          AS rooms,
      (SELECT count(*) FROM rooms WHERE NOT inaktiv)        AS rooms_aktiv,
      (SELECT count(*) FROM inspections)                    AS inspections,
      (SELECT count(*) FROM stundenzettel)                  AS zettel,
      (SELECT max(updated_at) FROM rooms)                   AS rooms_stand,
      (SELECT max(created_at) FROM inspections)             AS insp_stand`)).rows[0];
  const dateien = listFiles(FILES_DIR, FILES_DIR);
  res.json({
    serverUrl: host ? `${proto}://${host}${BASE_PATH}` : BASE_PATH,
    apiKey: API_KEY,
    daten: {
      zimmer: Number(agg.rooms), zimmerAktiv: Number(agg.rooms_aktiv),
      pruefberichte: Number(agg.inspections), stundenzettel: Number(agg.zettel),
      dateien: dateien.length,
      dateienMb: Math.round(dateien.reduce((s, f) => s + f.size, 0) / 1048576 * 10) / 10,
      zimmerStand: agg.rooms_stand || null,
      pruefungenStand: agg.insp_stand || null,
    },
  });
});

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

// ---------- Statische Weboberfläche ----------

router.use(express.static(path.join(__dirname, '..', 'public')));

app.use(BASE_PATH || '/', router);
if (BASE_PATH) app.get(BASE_PATH, (req, res) => res.redirect(`${BASE_PATH}/`));
app.get('/', (req, res) => res.redirect(`${BASE_PATH}/`));

init().then(async () => {
  await seedFirstUser();
  app.listen(PORT, () => console.log(`KKH-Server läuft auf Port ${PORT} unter ${BASE_PATH}/`));
}).catch((e) => {
  console.error('Start fehlgeschlagen:', e);
  process.exit(1);
});
