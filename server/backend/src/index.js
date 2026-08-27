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
      const relPfad = path.relative(base, voll).split(path.sep).join('/');
      // Signaturen werden NICHT in Dateilisten für die Web-API aufgenommen
      if (relPfad.includes('_signaturen')) continue;
      const st = fs.statSync(voll);
      ergebnis.push({ path: relPfad, size: st.size });
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

// Pull für die App (Web-Edits erreichen so alle Geräte, LWW über updatedAt)
router.get('/api/sync/stundenzettel', requireApiKey, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM stundenzettel');
  res.json({
    zettel: rows.map((z) => ({
      station: z.station, zeitraumStart: z.zeitraum_start,
      auftragsnummer: z.auftragsnummer, datum: z.datum,
      stunden: z.stunden, anfahrt: z.anfahrt, techniker: z.techniker,
      updatedAt: z.updated_at,
    })),
  });
});

router.get('/api/sync/files', requireApiKey, (req, res) => {
  res.json({ files: listFiles(FILES_DIR, FILES_DIR) });
});

router.put('/api/sync/file', requireApiKey,
  express.raw({ type: () => true, limit: '300mb' }), (req, res) => {
    const rel = String(req.query.path || '');
    const ziel = safeFilePath(rel);
    if (!ziel || !req.query.path) return res.status(400).json({ error: 'Ungültiger Pfad' });
    fs.mkdirSync(path.dirname(ziel), { recursive: true });
    fs.writeFileSync(ziel, req.body);
    kiFotoEinreihen(rel);
    res.json({ ok: true });
  });

// Neues Foto in die KI-Analyse-Warteschlange stellen (der KI-Worker
// unter server/ki/ holt sich die Jobs direkt aus der Datenbank).
// Signaturen werden grundsätzlich NICHT analysiert.
function kiFotoEinreihen(rel) {
  if (!/\.(jpe?g|png)$/i.test(rel) || rel.includes('_signaturen')) return;
  const roomId = rel.startsWith('_') ? '' : rel.split('/')[0];
  pool.query(
    `INSERT INTO foto_analysen (pfad, room_id) VALUES ($1, $2)
     ON CONFLICT (pfad) DO UPDATE SET status='wartet', fehler=''`,
    [rel, roomId]
  ).catch((e) => console.error('KI-Einreihung fehlgeschlagen:', e.message));
}

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

// API-Schlüssel für die App-Einrichtung – nur für eingeloggte Admins.
// Bewusst NUR der Sync-API-Key, nicht Admin-/DB-Passwort.
router.get('/api/web/apikey', requireWebAuth, (req, res) => res.json({ apiKey: API_KEY }));

router.patch('/api/web/me/password', requireWebAuth, express.json(), async (req, res) => {
  const { oldPassword, newPassword } = req.body || {};
  if (!newPassword || newPassword.length < 6) return res.status(400).json({ error: 'Neues Passwort muss mindestens 6 Zeichen haben' });
  const user = await findUser(req.username);
  if (!user) return res.status(404).json({ error: 'Benutzer nicht gefunden' });
  if (oldPassword && !verifyPassword(oldPassword, user.password_hash, user.salt)) {
    return res.status(401).json({ error: 'Aktuelles Passwort falsch' });
  }
  const { hash, salt } = hashPassword(newPassword);
  await pool.query('UPDATE users SET password_hash=$1, salt=$2, updated_at=now() WHERE id=$3',
    [hash, salt, user.id]);
  res.json({ ok: true });
});

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
  const neuPw = req.body.password ?? req.body.newPassword; // Kompatibilität
  if (neuPw !== undefined) {
    if (String(neuPw).length < 4) return res.status(400).json({ error: 'Passwort muss mindestens 4 Zeichen haben' });
    const { hash, salt } = hashPassword(neuPw);
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
    `SELECT uuid, datum, daten, mitarbeiter FROM inspections
     WHERE room_id=$1 AND COALESCE(geloescht, FALSE) = FALSE
     ORDER BY datum DESC`, [req.params.id])).rows;
  let sperren = [];
  try {
    sperren = (await pool.query(
      `SELECT * FROM sperren WHERE room_id=$1 AND COALESCE(aufgehoben, FALSE) = FALSE ORDER BY created_at DESC`,
      [req.params.id])).rows;
  } catch {}
  const dateien = listFiles(path.join(FILES_DIR, req.params.id), FILES_DIR);
  res.json({ room: roomToJson(rows[0]), inspections, sperren, files: dateien });
});

router.get('/api/web/inspections', requireWebAuth, async (req, res) => {
  const limit = Math.min(Number(req.query.limit || 300), 2000);
  const von = req.query.von || '';
  const bis = req.query.bis || '';
  const station = req.query.station || '';
  const mitarbeiter = req.query.mitarbeiter || '';
  const nurAktiv = req.query.geloescht !== '1';
  let sql = `SELECT i.uuid, i.room_id, i.datum, i.daten, i.mitarbeiter, i.geloescht,
               r.station, r.zimmer, r.tv_typ
             FROM inspections i LEFT JOIN rooms r ON r.id = i.room_id
             WHERE 1=1`;
  const werte = [];
  if (nurAktiv) sql += ` AND COALESCE(i.geloescht, FALSE) = FALSE`;
  if (von) { werte.push(von); sql += ` AND i.datum >= $${werte.length}`; }
  if (bis) { werte.push(bis); sql += ` AND i.datum <= $${werte.length}`; }
  if (station) { werte.push(station); sql += ` AND r.station = $${werte.length}`; }
  if (mitarbeiter) { werte.push(`%${mitarbeiter}%`); sql += ` AND i.mitarbeiter ILIKE $${werte.length}`; }
  sql += ` ORDER BY i.datum DESC, i.created_at DESC LIMIT $${werte.length + 1}`;
  werte.push(limit);
  const { rows } = await pool.query(sql, werte);
  res.json({ inspections: rows });
});

// Einzelne Prüfung
router.get('/api/web/inspections/:uuid', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query(
    `SELECT i.*, r.station, r.zimmer, r.tv_typ
     FROM inspections i LEFT JOIN rooms r ON r.id = i.room_id
     WHERE i.uuid = $1`, [req.params.uuid]);
  if (!rows.length) return res.status(404).json({ error: 'Nicht gefunden' });
  res.json({ inspection: rows[0] });
});

// Prüfung löschen (Soft-Delete)
router.delete('/api/web/inspections/:uuid', requireWebAuth, async (req, res) => {
  await pool.query('UPDATE inspections SET geloescht=TRUE WHERE uuid=$1', [req.params.uuid]);
  res.json({ ok: true });
});

router.get('/api/web/stundenzettel', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query(
    'SELECT * FROM stundenzettel ORDER BY zeitraum_start DESC, station');
  // Team-Summen je Zettel (für die Listenansicht)
  const { rows: eintraege } = await pool.query('SELECT * FROM zettel_eintraege');
  const summen = {};
  for (const e of eintraege) {
    const key = `${e.station}|${e.zeitraum_start}`;
    if (!summen[key]) summen[key] = { anzahl: 0, stunden: 0, anfahrt: 0 };
    summen[key].anzahl++;
    const parseStd = (s) => Number(String(s || '').replace(',', '.')) || 0;
    summen[key].stunden += parseStd(e.stunden);
    summen[key].anfahrt += parseStd(e.anfahrt);
  }
  res.json({
    zettel: rows.map((z) => ({
      ...z,
      team: summen[`${z.station}|${z.zeitraum_start}`] || { anzahl: 0, stunden: 0, anfahrt: 0 },
    })),
  });
});

// Nächste freie Auftragsnummer (A-JJJJ-NNNN)
async function naechsteAuftragsnummer() {
  const jahr = new Date().getFullYear();
  const { rows } = await pool.query(
    `SELECT auftragsnummer FROM stundenzettel
     WHERE auftragsnummer LIKE $1 ORDER BY auftragsnummer DESC LIMIT 1`,
    [`A-${jahr}-%`]);
  let nr = 1;
  if (rows[0]) {
    const m = /A-\d{4}-(\d+)/.exec(rows[0].auftragsnummer || '');
    if (m) nr = Number(m[1]) + 1;
  }
  return `A-${jahr}-${String(nr).padStart(4, '0')}`;
}

router.get('/api/web/stundenzettel/next-nr', requireWebAuth, async (req, res) => {
  const nr_str = await naechsteAuftragsnummer();
  res.json({ auftragsnummer: nr_str, nr: nr_str }); // nr für Kompatibilität
});

// Auch für die App (koordinierte Nummern über mehrere Geräte, ab App 1.9.5)
router.get('/api/sync/naechste-auftragsnummer', requireApiKey, async (req, res) => {
  res.json({ auftragsnummer: await naechsteAuftragsnummer() });
});

// Lager-Artikel unter Mindestbestand – Nachbestell-Warnung in der App (ab 1.9.5)
router.get('/api/sync/lager-status', requireApiKey, async (req, res) => {
  const { rows } = await pool.query(
    `SELECT bezeichnung, bestand, mindestbestand, einheit FROM lager_artikel
     WHERE aktiv AND mindestbestand > 0 AND bestand < mindestbestand
     ORDER BY bezeichnung`);
  res.json({
    knapp: rows.map((r) => ({
      bezeichnung: r.bezeichnung,
      bestand: String(Number(r.bestand)),
      mindestbestand: String(Number(r.mindestbestand)),
      einheit: r.einheit,
    })),
  });
});

// Detail: Header + Team-Zeilen + Prüfungen der Station im Zeitraum
router.get('/api/web/stundenzettel/detail', requireWebAuth, async (req, res) => {
  const station = String(req.query.station || '');
  const zeitraum = String(req.query.zeitraum || '');
  if (!station || !zeitraum) return res.status(400).json({ error: 'station und zeitraum nötig' });
  const { rows } = await pool.query(
    'SELECT * FROM stundenzettel WHERE station=$1 AND zeitraum_start=$2', [station, zeitraum]);
  if (!rows[0]) return res.status(404).json({ error: 'Stundenzettel nicht gefunden' });
  const { rows: eintraege } = await pool.query(
    `SELECT * FROM zettel_eintraege WHERE station=$1 AND zeitraum_start=$2
     ORDER BY mitarbeiter`, [station, zeitraum]);
  // Nächster Zettel derselben Station begrenzt den Zeitraum
  const { rows: naechste } = await pool.query(
    `SELECT zeitraum_start FROM stundenzettel
     WHERE station=$1 AND zeitraum_start > $2 ORDER BY zeitraum_start LIMIT 1`,
    [station, zeitraum]);
  const ende = naechste[0]?.zeitraum_start || null;
  const { rows: inspections } = ende
    ? await pool.query(
        `SELECT i.uuid, i.room_id, i.datum, i.daten, i.mitarbeiter, r.zimmer
         FROM inspections i LEFT JOIN rooms r ON r.id = i.room_id
         WHERE r.station=$1 AND i.datum >= $2 AND i.datum < $3
           AND COALESCE(i.geloescht, FALSE) = FALSE
         ORDER BY i.datum, r.zimmer`, [station, zeitraum, ende])
    : await pool.query(
        `SELECT i.uuid, i.room_id, i.datum, i.daten, i.mitarbeiter, r.zimmer
         FROM inspections i LEFT JOIN rooms r ON r.id = i.room_id
         WHERE r.station=$1 AND i.datum >= $2
           AND COALESCE(i.geloescht, FALSE) = FALSE
         ORDER BY i.datum, r.zimmer`, [station, zeitraum]);
  const { rows: mitarbeiter } = await pool.query(
    'SELECT * FROM mitarbeiter WHERE aktiv = TRUE ORDER BY name');
  res.json({
    zettel: rows[0],
    eintraege,
    mitarbeiter,
    zeitraumEnde: ende,
    inspections: inspections.map((i) => ({
      uuid: i.uuid, roomId: i.room_id, zimmer: i.zimmer, datum: i.datum,
      mitarbeiter: i.mitarbeiter || '',
      arbeiten: (i.daten || {}).arbeiten || [],
      bemerkungen: (i.daten || {}).bemerkungen || '',
    })),
  });
});

// Anlegen / Speichern (Web) – setzt updated_at neu (gewinnt gegen ältere App-Stände)
router.put('/api/web/stundenzettel', requireWebAuth, express.json(), async (req, res) => {
  const b = req.body || {};
  const station = String(b.station || '').trim();
  const zeitraum = String(b.zeitraum_start || b.zeitraumStart || '').trim();
  if (!station || !zeitraum) {
    return res.status(400).json({ error: 'Station und Zeitraum-Beginn sind Pflicht' });
  }
  const updatedAt = nowIso();
  await pool.query(
    `INSERT INTO stundenzettel (station, zeitraum_start, auftragsnummer, datum,
                                stunden, anfahrt, techniker, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
     ON CONFLICT (station, zeitraum_start) DO UPDATE SET
       auftragsnummer=EXCLUDED.auftragsnummer, datum=EXCLUDED.datum,
       stunden=EXCLUDED.stunden, anfahrt=EXCLUDED.anfahrt,
       techniker=EXCLUDED.techniker, updated_at=EXCLUDED.updated_at`,
    [station, zeitraum, String(b.auftragsnummer || '').trim(),
     String(b.datum || '').trim(), String(b.stunden || '').trim(),
     String(b.anfahrt || '').trim(), String(b.techniker || '').trim(), updatedAt]);
  const { rows } = await pool.query(
    'SELECT * FROM stundenzettel WHERE station=$1 AND zeitraum_start=$2', [station, zeitraum]);
  res.json({ ok: true, zettel: rows[0] });
});

router.delete('/api/web/stundenzettel', requireWebAuth, async (req, res) => {
  const station = String(req.query.station || '');
  const zeitraum = String(req.query.zeitraum || '');
  if (!station || !zeitraum) return res.status(400).json({ error: 'station und zeitraum nötig' });
  await pool.query('DELETE FROM zettel_eintraege WHERE station=$1 AND zeitraum_start=$2',
    [station, zeitraum]);
  await pool.query('DELETE FROM stundenzettel WHERE station=$1 AND zeitraum_start=$2',
    [station, zeitraum]);
  res.json({ ok: true });
});

router.get('/api/web/file', requireWebAuth, (req, res) => {
  const rel = String(req.query.path || '');
  // Signaturen dürfen NICHT über die Web-API ausgeliefert werden
  if (rel.includes('_signaturen')) return res.status(403).json({ error: 'Zugriff auf Signaturen nicht erlaubt' });
  const ziel = safeFilePath(rel);
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
  await replaceAll('sperren', ['room_id', 'gesperrt_am', 'grund', 'wiedervorlage'],
    (req.body.sperren || []).map((s) => ({
      room_id: s.roomId, gesperrt_am: s.gesperrtAm || '', grund: s.grund || '',
      wiedervorlage: s.wiedervorlage || '' })));
  res.json({ ok: true });
});

// Sperren für die App zum Herunterladen (Mehrgerät: Kein-Zutritt vom einen
// Gerät sichtbar auf dem anderen).
router.get('/api/sync/sperren', requireApiKey, async (req, res) => {
  const { rows } = await pool.query(
    'SELECT room_id, gesperrt_am, grund, wiedervorlage FROM sperren');
  res.json({
    sperren: rows.map((r) => ({
      roomId: r.room_id, gesperrtAm: r.gesperrt_am,
      grund: r.grund || '', wiedervorlage: r.wiedervorlage || '',
    })),
  });
});

// Material-Bestand ist bidirektional (LWW über updated_at): die App schickt
// ihren Stand, der Server übernimmt nur neuere Zeilen und spiegelt den Bestand
// in einen verknüpften Lager-Artikel (app_material_name), damit der Chef ihn im
// Web-Lager sieht. Der Rückweg (Web → App) läuft über spiegleLagerNachMaterial().
async function spiegleMaterialNachLager(name, bestand) {
  // Nur wenn ein aktiver Lager-Artikel verknüpft ist und sich der Bestand ändert
  const { rows } = await pool.query(
    `SELECT id, bestand FROM lager_artikel
     WHERE lower(app_material_name) = lower($1) AND aktiv = TRUE LIMIT 1`, [name]);
  if (!rows.length) return;
  if (Number(rows[0].bestand) === Number(bestand)) return;
  await pool.query('UPDATE lager_artikel SET bestand=$1, updated_at=now() WHERE id=$2',
    [bestand, rows[0].id]);
  await pool.query(
    `INSERT INTO lager_buchungen (artikel_id, typ, menge, grund, benutzer)
     VALUES ($1,'korrektur',$2,'Abgleich aus der App','App-Sync')`,
    [rows[0].id, bestand]).catch(() => {});
}

// Zeitstempel im App-Format (Berlin, naive ISO) für Alt-Clients ohne updatedAt.
function berlinStamp() {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Berlin', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).formatToParts(new Date());
  const g = (t) => parts.find((p) => p.type === t).value;
  return `${g('year')}-${g('month')}-${g('day')}T${g('hour')}:${g('minute')}:${g('second')}`;
}

router.post('/api/sync/material', requireApiKey, express.json({ limit: '5mb' }), async (req, res) => {
  let uebernommen = 0;
  for (const m of req.body.material || []) {
    if (!m.name) continue;
    // Rückwärtskompatibel: Alt-Client (1.9.x) sendet kein updatedAt -> wir setzen
    // "jetzt" ein, damit seine Änderung wie früher (Replace-All) immer übernommen
    // wird. 2.0-Clients senden ihren echten Zeitstempel (echtes LWW).
    const uedt = String(m.updatedAt || '').trim() || berlinStamp();
    const r = await pool.query(
      `INSERT INTO material (name, bestand, bestand_aktiv, aktiv, sort_index, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (name) DO UPDATE SET
         bestand=EXCLUDED.bestand, bestand_aktiv=EXCLUDED.bestand_aktiv,
         aktiv=EXCLUDED.aktiv, sort_index=EXCLUDED.sort_index, updated_at=EXCLUDED.updated_at
       WHERE material.updated_at < EXCLUDED.updated_at
       RETURNING name, bestand`,
      [m.name, m.bestand || 0, !!m.bestandAktiv, m.aktiv !== false,
       m.sortIndex || 0, uedt]);
    if (r.rowCount > 0) {
      uebernommen++;
      await spiegleMaterialNachLager(r.rows[0].name, r.rows[0].bestand);
    }
  }
  res.json({ ok: true, uebernommen });
});

// Material-Stand (mit Bestand) für die App zum Herunterladen – so kommen im
// Web (oder Lager) geänderte Bestände zurück aufs Gerät.
router.get('/api/sync/material', requireApiKey, async (req, res) => {
  const { rows } = await pool.query(
    'SELECT name, bestand, bestand_aktiv, aktiv, sort_index, updated_at FROM material');
  res.json({
    material: rows.map((r) => ({
      name: r.name, bestand: Number(r.bestand), bestandAktiv: r.bestand_aktiv,
      aktiv: r.aktiv, sortIndex: r.sort_index, updatedAt: r.updated_at || '',
    })),
  });
});

// Lieferanten für die App (nur lesen) – aus dem im Web gepflegten Lager.
router.get('/api/sync/lieferanten', requireApiKey, async (req, res) => {
  const { rows } = await pool.query(
    `SELECT name, kontakt, telefon, email, kundennummer, notiz
     FROM lieferanten WHERE aktiv = TRUE ORDER BY name`);
  res.json({ lieferanten: rows });
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

// Team-Zeile speichern (Web) – LWW mit frischem updated_at
router.put('/api/web/zettel-eintraege', requireWebAuth, express.json(), async (req, res) => {
  const liste = Array.isArray(req.body.eintraege) ? req.body.eintraege : [req.body];
  let gespeichert = 0;
  const updatedAt = nowIso();
  for (const e of liste) {
    const station = String(e.station || '').trim();
    const zeitraum = String(e.zeitraum_start || e.zeitraumStart || '').trim();
    const mitarbeiter = String(e.mitarbeiter || '').trim();
    if (!station || !zeitraum || !mitarbeiter) continue;
    // Zettel-Header sicherstellen (falls nur Team-Zeile angelegt wird)
    await pool.query(
      `INSERT INTO stundenzettel (station, zeitraum_start, updated_at)
       VALUES ($1,$2,$3) ON CONFLICT (station, zeitraum_start) DO NOTHING`,
      [station, zeitraum, updatedAt]);
    await pool.query(
      `INSERT INTO zettel_eintraege (station, zeitraum_start, mitarbeiter, stunden, anfahrt, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (station, zeitraum_start, mitarbeiter) DO UPDATE SET
         stunden=EXCLUDED.stunden, anfahrt=EXCLUDED.anfahrt, updated_at=EXCLUDED.updated_at`,
      [station, zeitraum, mitarbeiter, String(e.stunden || '').trim(),
       String(e.anfahrt || '').trim(), updatedAt]);
    gespeichert++;
  }
  res.json({ ok: true, gespeichert });
});

router.delete('/api/web/zettel-eintraege', requireWebAuth, async (req, res) => {
  const station = String(req.query.station || '');
  const zeitraum = String(req.query.zeitraum || '');
  const mitarbeiter = String(req.query.mitarbeiter || '');
  if (!station || !zeitraum || !mitarbeiter) {
    return res.status(400).json({ error: 'station, zeitraum und mitarbeiter nötig' });
  }
  await pool.query(
    'DELETE FROM zettel_eintraege WHERE station=$1 AND zeitraum_start=$2 AND mitarbeiter=$3',
    [station, zeitraum, mitarbeiter]);
  res.json({ ok: true });
});

// ---------- Excero-Webapp-Modul (eigener Pfad /excero/) ----------
// Alle Excero-spezifischen Routen werden im exceroRouter registriert
// und unter /excero/ eingehängt (siehe unten nach dem KKH-Router).
const exceroRouter = express.Router();

// Gleiche Cookie-Auth wie KKH (kkh_session wird von beiden Systemen genutzt)
const exceroAuth = (req, res, next) => {
  const username = checkSession(parseCookies(req).kkh_session);
  if (username) { req.username = username; return next(); }
  res.status(401).json({ error: 'Nicht angemeldet' });
};

const firmenRouter  = require('./routes/firmen');
const baulRouter    = require('./routes/baustellen');
const zeitRouter    = require('./routes/zeiterfassung');
const finRouter     = require('./routes/finanzen');
const rechnRouter   = require('./routes/rechnungen');
const hidriveRouter = require('./routes/hidrive');

exceroRouter.use('/api/web', exceroAuth, firmenRouter);
exceroRouter.use('/api/web', exceroAuth, baulRouter);
exceroRouter.use('/api/web', exceroAuth, zeitRouter);
exceroRouter.use('/api/web', exceroAuth, finRouter);
exceroRouter.use('/api/web', exceroAuth, rechnRouter);
exceroRouter.use('/api/web', exceroAuth, hidriveRouter);

// ---------- Stundenzettel-PDF (App-Upload bevorzugt, sonst server-seitig erzeugt) ----------
const { erzeugePdf: erzeugeStundenzettelPdf } = require('./pdf/stundenzettel');

router.get('/api/web/stundenzettel/pdf', requireWebAuth, async (req, res) => {
  const station = String(req.query.station || '');
  const zeitraum = String(req.query.zeitraum || '');
  if (!station || !zeitraum) return res.status(400).json({ error: 'station + zeitraum nötig' });
  const stationSafe = station.replace(/[^A-Za-z0-9äöüÄÖÜß_-]/g, '_');
  const dateiname = `Stundenzettel_${stationSafe}_${zeitraum}.pdf`;

  // Bevorzugt: App-hochgeladene Datei
  const appPdf = safeFilePath(`_stundenzettel/${dateiname}`);
  if (appPdf && fs.existsSync(appPdf)) {
    return res.sendFile(appPdf, { headers: { 'Content-Disposition': `inline; filename="${dateiname}"` } });
  }

  // Fallback: server-seitig erzeugen
  try {
    const [zettelQ, eintraegeQ, inspQ] = await Promise.all([
      pool.query('SELECT * FROM stundenzettel WHERE station=$1 AND zeitraum_start=$2', [station, zeitraum]),
      pool.query('SELECT * FROM zettel_eintraege WHERE station=$1 AND zeitraum_start=$2', [station, zeitraum]),
      pool.query(
        `SELECT i.datum, r.zimmer, i.daten->'arbeiten' AS arbeiten
         FROM inspections i JOIN rooms r ON r.id=i.room_id
         WHERE r.station=$1 AND i.datum>=$2 AND COALESCE(i.geloescht,FALSE)=FALSE
         ORDER BY i.datum, r.zimmer`, [station, zeitraum]),
    ]);
    if (!zettelQ.rows.length) return res.status(404).json({ error: 'Stundenzettel nicht gefunden' });
    const z = zettelQ.rows[0];

    // Signaturen: deterministischer Pfad seit v1.9.3
    const sigSta = safeFilePath(`_signaturen/${stationSafe}_${zeitraum}_station.png`);
    const sigTech = safeFilePath(`_signaturen/${stationSafe}_${zeitraum}_techniker.png`);

    // Material-Zusammenfassung
    const matMap = {};
    for (const i of inspQ.rows) {
      for (const a of (i.arbeiten || [])) { matMap[a] = (matMap[a] || 0) + 1; }
    }

    const pdf = await erzeugeStundenzettelPdf({
      station, zeitraum: `ab ${zeitraum}`, zeitraumStart: zeitraum,
      auftragsnummer: z.auftragsnummer, datum: z.datum, techniker: z.techniker,
      eintraege: eintraegeQ.rows.map((e) => ({
        mitarbeiter: e.mitarbeiter, stunden: e.stunden, anfahrt: e.anfahrt
      })),
      leistungen: inspQ.rows.map((i) => ({
        zimmer: i.zimmer, datum: i.datum,
        arbeiten: (i.arbeiten || []).filter((a) => typeof a === 'string'),
      })),
      material: Object.entries(matMap).map(([bezeichnung, anzahl]) => ({ bezeichnung, anzahl })),
      signaturStation: sigSta && fs.existsSync(sigSta) ? sigSta : null,
      signaturTechniker: sigTech && fs.existsSync(sigTech) ? sigTech : null,
    });

    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `inline; filename="${dateiname}"`);
    res.send(pdf);
  } catch (e) {
    console.error('PDF-Erzeugung fehlgeschlagen:', e.message);
    res.status(500).json({ error: e.message });
  }
});

// ---------- Rechnungs-PDF (Excero-Router) ----------
const { erzeugePdf: erzeugeRechnungsPdf } = require('./pdf/rechnung');

exceroRouter.get('/api/web/rechnungen/:id/pdf', exceroAuth, async (req, res) => {
  const { rows: r } = await pool.query(
    `SELECT re.*, f.name AS firma_name, f.adresse AS firma_adresse, f.email AS firma_email,
            f.telefon AS firma_telefon, f.steuernummer, f.ust_id, f.besteuerung, f.ust_satz,
            f.iban, f.bic, f.bank_name, f.rechnungs_fusstext, f.logo_pfad
     FROM rechnungen re LEFT JOIN firmen f ON f.id=re.firma_id WHERE re.id=$1`,
    [Number(req.params.id)]);
  if (!r.length) return res.status(404).json({ error: 'Nicht gefunden' });
  const { rows: pos } = await pool.query(
    'SELECT * FROM rechnung_positionen WHERE rechnung_id=$1 ORDER BY pos',
    [Number(req.params.id)]);
  const pdf = await erzeugeRechnungsPdf(r[0], pos);
  res.setHeader('Content-Type', 'application/pdf');
  res.setHeader('Content-Disposition', `inline; filename="Rechnung_${r[0].nummer}.pdf"`);
  res.send(pdf);
});

// ---------- Rechnungs-E-Mail-Versand (Excero-Router) ----------
let nodemailer = null;
try { nodemailer = require('nodemailer'); } catch { console.log('nodemailer nicht verfügbar'); }

exceroRouter.post('/api/web/rechnungen/:id/versenden', exceroAuth, express.json(), async (req, res) => {
  if (!nodemailer) return res.status(503).json({ error: 'nodemailer nicht installiert' });
  const id = Number(req.params.id);
  const { rows: r } = await pool.query(
    `SELECT re.*, f.name AS firma_name, f.adresse AS firma_adresse, f.email AS firma_email,
            f.telefon AS firma_telefon, f.steuernummer, f.ust_id, f.besteuerung, f.ust_satz,
            f.iban, f.bic, f.bank_name, f.rechnungs_fusstext, f.logo_pfad
     FROM rechnungen re LEFT JOIN firmen f ON f.id=re.firma_id WHERE re.id=$1`, [id]);
  if (!r.length) return res.status(404).json({ error: 'Rechnung nicht gefunden' });
  const re = r[0];
  const { rows: pos } = await pool.query('SELECT * FROM rechnung_positionen WHERE rechnung_id=$1 ORDER BY pos', [id]);

  // SMTP aus Einstellungen
  const { rows: smtp } = await pool.query("SELECT wert FROM einstellungen WHERE key='smtp'");
  const smtpCfg = smtp[0]?.wert;
  if (!smtpCfg?.host) return res.status(503).json({ error: 'SMTP nicht konfiguriert (Einstellungen → SMTP)' });

  const empfaenger = req.body?.an || re.kunde_email;
  if (!empfaenger) return res.status(400).json({ error: 'Empfänger-E-Mail fehlt' });

  const pdf = await erzeugeRechnungsPdf(re, pos);
  const betreff = req.body?.betreff || `Rechnung ${re.nummer} von ${re.firma_name}`;
  const text = req.body?.text || `Sehr geehrte Damen und Herren,\n\nerbeten finde ich anbei die Rechnung ${re.nummer}.\n\nMit freundlichen Grüßen\n${re.firma_name}`;

  const transporter = nodemailer.createTransporter({
    host: smtpCfg.host,
    port: Number(smtpCfg.port) || 587,
    secure: smtpCfg.secure === true,
    auth: { user: smtpCfg.user, pass: smtpCfg.passwort },
  });

  await transporter.sendMail({
    from: smtpCfg.absender || smtpCfg.user,
    to: empfaenger,
    subject: betreff,
    text,
    attachments: [{ filename: `Rechnung_${re.nummer}.pdf`, content: pdf }],
  });

  await pool.query(
    "UPDATE rechnungen SET status='versendet', versendet_am=now(), geaendert_am=now() WHERE id=$1", [id]);
  res.json({ ok: true });
});

// ---------- Foto-ZIP-Export (KKH – bleibt im KKH-Router, Fotos sind KKH-Daten) ----------
let archiver = null;
try { archiver = require('archiver'); } catch { console.log('archiver nicht verfügbar'); }

router.get('/api/web/export/fotos.zip', requireWebAuth, (req, res) => {
  if (!archiver) return res.status(503).json({ error: 'archiver nicht installiert' });
  const station = String(req.query.station || '');
  const zimmer  = String(req.query.zimmer || '');
  const von     = String(req.query.von || '');
  const bis     = String(req.query.bis || '');

  res.setHeader('Content-Type', 'application/zip');
  res.setHeader('Content-Disposition', `attachment; filename="KKH-Fotos${station ? '-' + station : ''}.zip"`);

  const archive = archiver('zip', { zlib: { level: 6 } });
  archive.pipe(res);
  archive.on('error', (e) => { console.error('ZIP-Fehler:', e.message); });

  // Dateien aus FILES_DIR durchlaufen
  const root = fs.existsSync(FILES_DIR) ? FILES_DIR : null;
  if (!root) { archive.finalize(); return; }

  const dateien = listFiles(root, root);
  for (const datei of dateien) {
    const p = datei.path;
    if (!/\.(jpe?g|png)$/i.test(p)) continue;
    const teile = p.split('/');
    // Struktur: <Station_Zimmer>/<JJJJMMTT>/Foto.jpg
    if (teile.length < 2) continue;
    const zimmerOrdner = teile[0]; // z. B. A4_01a
    const datum        = teile[1]; // z. B. 20260715
    if (station && !zimmerOrdner.startsWith(station)) continue;
    if (zimmer && !zimmerOrdner.includes(zimmer)) continue;
    if (von && datum < von.replace(/-/g, '')) continue;
    if (bis && datum > bis.replace(/-/g, '')) continue;
    const abs = path.join(root, p);
    if (fs.existsSync(abs)) archive.file(abs, { name: p });
  }
  archive.finalize();
});

// ---------- Lager-Modul ----------

// Lieferanten
router.get('/api/web/lieferanten', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM lieferanten ORDER BY name');
  res.json({ lieferanten: rows });
});
router.post('/api/web/lieferanten', requireWebAuth, express.json(), async (req, res) => {
  const b = req.body || {};
  const name = String(b.name || '').trim();
  if (!name) return res.status(400).json({ error: 'Name ist Pflicht' });
  const { rows } = await pool.query(
    `INSERT INTO lieferanten (name, kontakt, telefon, email, kundennummer, notiz)
     VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
    [name, b.kontakt || '', b.telefon || '', b.email || '', b.kundennummer || '', b.notiz || '']);
  res.json({ ok: true, lieferant: rows[0] });
});
router.patch('/api/web/lieferanten/:id', requireWebAuth, express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const b = req.body || {};
  const felder = [], werte = [];
  const add = (k, v) => { felder.push(`${k}=$${felder.length + 1}`); werte.push(v); };
  if (b.name !== undefined) add('name', String(b.name).trim());
  if (b.kontakt !== undefined) add('kontakt', String(b.kontakt));
  if (b.telefon !== undefined) add('telefon', String(b.telefon));
  if (b.email !== undefined) add('email', String(b.email));
  if (b.kundennummer !== undefined) add('kundennummer', String(b.kundennummer));
  if (b.notiz !== undefined) add('notiz', String(b.notiz));
  if (b.aktiv !== undefined) add('aktiv', !!b.aktiv);
  if (!felder.length) return res.status(400).json({ error: 'Nichts zum Aktualisieren' });
  werte.push(id);
  await pool.query(`UPDATE lieferanten SET ${felder.join(',')} WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});
router.delete('/api/web/lieferanten/:id', requireWebAuth, async (req, res) => {
  await pool.query('UPDATE lieferanten SET aktiv=FALSE WHERE id=$1', [Number(req.params.id)]);
  res.json({ ok: true });
});

// Lager-Artikel
router.get('/api/web/lager/artikel', requireWebAuth, async (req, res) => {
  const nurWarn = req.query.nachbestellung === '1';
  const q = String(req.query.suche || '');
  const kat = String(req.query.kategorie || '');
  let sql = `SELECT a.*, l.name AS lieferant_name
             FROM lager_artikel a LEFT JOIN lieferanten l ON l.id = a.lieferant_id
             WHERE a.aktiv = TRUE`;
  const werte = [];
  if (nurWarn) { werte.push(0); sql += ` AND a.bestand <= a.mindestbestand`; }
  if (q) { werte.push(`%${q}%`); sql += ` AND (a.bezeichnung ILIKE $${werte.length} OR a.artikelnummer ILIKE $${werte.length})`; }
  if (kat) { werte.push(kat); sql += ` AND a.kategorie = $${werte.length}`; }
  sql += ' ORDER BY a.kategorie, a.bezeichnung';
  const { rows } = await pool.query(sql, werte);
  const { rows: kats } = await pool.query(
    `SELECT DISTINCT kategorie FROM lager_artikel WHERE aktiv=TRUE AND kategorie!='' ORDER BY kategorie`);
  res.json({ artikel: rows, kategorien: kats.map((r) => r.kategorie) });
});
router.post('/api/web/lager/artikel', requireWebAuth, express.json(), async (req, res) => {
  const b = req.body || {};
  const bez = String(b.bezeichnung || '').trim();
  if (!bez) return res.status(400).json({ error: 'Bezeichnung ist Pflicht' });
  const { rows } = await pool.query(
    `INSERT INTO lager_artikel
       (bezeichnung, artikelnummer, kategorie, einheit, ek_preis, vk_preis,
        bestand, mindestbestand, lieferant_id, app_material_name, notiz)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING *`,
    [bez, b.artikelnummer || '', b.kategorie || '', b.einheit || 'Stk.',
     b.ek_preis || null, b.vk_preis || null,
     Number(b.bestand) || 0, Number(b.mindestbestand) || 0,
     b.lieferant_id || null, b.app_material_name || '', b.notiz || '']);
  res.json({ ok: true, artikel: rows[0] });
});
router.patch('/api/web/lager/artikel/:id', requireWebAuth, express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const b = req.body || {};
  const felder = [], werte = [];
  const add = (k, v) => { felder.push(`${k}=$${felder.length + 1}`); werte.push(v); };
  if (b.bezeichnung !== undefined) add('bezeichnung', String(b.bezeichnung).trim());
  if (b.artikelnummer !== undefined) add('artikelnummer', String(b.artikelnummer));
  if (b.kategorie !== undefined) add('kategorie', String(b.kategorie));
  if (b.einheit !== undefined) add('einheit', String(b.einheit));
  if (b.ek_preis !== undefined) add('ek_preis', b.ek_preis === '' ? null : Number(b.ek_preis));
  if (b.vk_preis !== undefined) add('vk_preis', b.vk_preis === '' ? null : Number(b.vk_preis));
  if (b.mindestbestand !== undefined) add('mindestbestand', Number(b.mindestbestand) || 0);
  if (b.lieferant_id !== undefined) add('lieferant_id', b.lieferant_id || null);
  if (b.app_material_name !== undefined) add('app_material_name', String(b.app_material_name));
  if (b.notiz !== undefined) add('notiz', String(b.notiz));
  if (b.aktiv !== undefined) add('aktiv', !!b.aktiv);
  add('updated_at', new Date().toISOString());
  if (felder.length < 2) return res.status(400).json({ error: 'Nichts zum Aktualisieren' });
  werte.push(id);
  await pool.query(`UPDATE lager_artikel SET ${felder.join(',')} WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});
router.delete('/api/web/lager/artikel/:id', requireWebAuth, async (req, res) => {
  await pool.query('UPDATE lager_artikel SET aktiv=FALSE WHERE id=$1', [Number(req.params.id)]);
  res.json({ ok: true });
});

// Buchungen (Ein-/Ausgang / Korrektur)
router.get('/api/web/lager/buchungen', requireWebAuth, async (req, res) => {
  const artikelId = req.query.artikel_id ? Number(req.query.artikel_id) : null;
  const von = String(req.query.von || '');
  const bis = String(req.query.bis || '');
  const limit = Math.min(Number(req.query.limit || 200), 2000);
  let sql = `SELECT b.*, a.bezeichnung, a.einheit
             FROM lager_buchungen b JOIN lager_artikel a ON a.id = b.artikel_id WHERE 1=1`;
  const werte = [];
  if (artikelId) { werte.push(artikelId); sql += ` AND b.artikel_id=$${werte.length}`; }
  if (von) { werte.push(von + 'T00:00:00'); sql += ` AND b.zeitpunkt >= $${werte.length}::timestamptz`; }
  if (bis) { werte.push(bis + 'T23:59:59'); sql += ` AND b.zeitpunkt <= $${werte.length}::timestamptz`; }
  sql += ` ORDER BY b.zeitpunkt DESC LIMIT $${werte.length + 1}`;
  werte.push(limit);
  const { rows } = await pool.query(sql, werte);
  res.json({ buchungen: rows });
});

router.post('/api/web/lager/buchung', requireWebAuth, express.json(), async (req, res) => {
  const b = req.body || {};
  const artikelId = Number(b.artikel_id);
  const typ = String(b.typ || '');
  const menge = Number(b.menge);
  if (!artikelId || !['eingang', 'ausgang', 'korrektur'].includes(typ) || !(menge > 0)) {
    return res.status(400).json({ error: 'artikel_id, typ (eingang/ausgang/korrektur), menge > 0 nötig' });
  }
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const delta = typ === 'ausgang' ? -menge : menge;
    if (typ === 'korrektur') {
      await client.query('UPDATE lager_artikel SET bestand=$1, updated_at=now() WHERE id=$2',
        [menge, artikelId]);
    } else {
      await client.query('UPDATE lager_artikel SET bestand=bestand+$1, updated_at=now() WHERE id=$2',
        [delta, artikelId]);
    }
    const { rows } = await client.query(
      `INSERT INTO lager_buchungen (artikel_id, typ, menge, ek_preis, grund, bezug, benutzer)
       VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`,
      [artikelId, typ, menge, b.ek_preis || null, b.grund || '', b.bezug || '',
       req.username || '']);
    // Neuen Bestand in den verknüpften App-Materialposten spiegeln (Web → App),
    // damit die geänderte Menge beim nächsten Sync auf den Geräten ankommt.
    await client.query(
      `UPDATE material m
         SET bestand = a.bestand,
             updated_at = to_char(now() AT TIME ZONE 'Europe/Berlin', 'YYYY-MM-DD"T"HH24:MI:SS')
       FROM lager_artikel a
       WHERE a.id = $1 AND a.app_material_name <> ''
         AND lower(m.name) = lower(a.app_material_name)
         AND m.bestand <> a.bestand`,
      [artikelId]);
    await client.query('COMMIT');
    res.json({ ok: true, buchung: rows[0] });
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally { client.release(); }
});

// Verbrauchsauswertung aus Prüfungen
router.get('/api/web/lager/verbrauch', requireWebAuth, async (req, res) => {
  const von = String(req.query.von || new Date(Date.now() - 30 * 86400e3).toISOString().slice(0, 10));
  const bis = String(req.query.bis || new Date().toISOString().slice(0, 10));
  const station = String(req.query.station || '');
  let sql = `
    SELECT r.station,
           elem.value AS material,
           COUNT(*)::int AS anzahl,
           a.ek_preis,
           ROUND((a.ek_preis * COUNT(*))::numeric, 2) AS gesamt_ek
    FROM inspections i
    JOIN rooms r ON r.id = i.room_id
    CROSS JOIN LATERAL jsonb_array_elements_text(i.daten->'arbeiten') AS elem(value)
    LEFT JOIN lager_artikel a ON lower(a.bezeichnung) = lower(elem.value) AND a.aktiv = TRUE
    WHERE COALESCE(i.geloescht, FALSE) = FALSE
      AND i.datum >= $1 AND i.datum <= $2`;
  const werte = [von, bis];
  if (station) { werte.push(station); sql += ` AND r.station = $${werte.length}`; }
  sql += ` GROUP BY r.station, elem.value, a.ek_preis ORDER BY r.station, anzahl DESC`;
  const { rows } = await pool.query(sql, werte);
  res.json({ verbrauch: rows, von, bis });
});

// Nachbestellungsliste
router.get('/api/web/lager/nachbestellung', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query(
    `SELECT a.*, l.name AS lieferant_name
     FROM lager_artikel a LEFT JOIN lieferanten l ON l.id = a.lieferant_id
     WHERE a.aktiv = TRUE AND a.mindestbestand > 0 AND a.bestand <= a.mindestbestand
     ORDER BY (a.bestand - a.mindestbestand) ASC, a.bezeichnung`);
  res.json({ artikel: rows, anzahl: rows.length });
});

// ---------- Export (XLSX / CSV) ----------
let ExcelJS = null;
try { ExcelJS = require('exceljs'); } catch { console.log('exceljs nicht verfügbar'); }

async function sendeXlsx(res, dateiname, sheets) {
  if (!ExcelJS) return res.status(503).json({ error: 'exceljs nicht installiert' });
  const wb = new ExcelJS.Workbook();
  wb.creator = 'KKH TV-Wartung';
  wb.created = new Date();
  for (const { name, columns, rows } of sheets) {
    const ws = wb.addWorksheet(name);
    ws.columns = columns.map((c) => ({
      header: c.header, key: c.key,
      width: c.width || 18,
      style: c.numFmt ? { numFmt: c.numFmt } : {},
    }));
    ws.getRow(1).font = { bold: true };
    ws.getRow(1).fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFE0F2EF' } };
    ws.getRow(1).border = { bottom: { style: 'thin' } };
    rows.forEach((r, i) => {
      const row = ws.addRow(r);
      if (i % 2 === 0) row.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFF4F8F6' } };
    });
  }
  res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  res.setHeader('Content-Disposition', `attachment; filename="${dateiname}"`);
  await wb.xlsx.write(res);
  res.end();
}

function sendeCsv(res, dateiname, headers, rows) {
  const csvEsc = (v) => {
    const s = v === null || v === undefined ? '' : String(v);
    return s.includes(',') || s.includes('"') || s.includes('\n') ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const lines = [headers.map(csvEsc).join(',')];
  rows.forEach((r) => lines.push(r.map(csvEsc).join(',')));
  res.setHeader('Content-Type', 'text/csv; charset=utf-8');
  res.setHeader('Content-Disposition', `attachment; filename="${dateiname}"`);
  res.send('\uFEFF' + lines.join('\r\n'));
}

router.get('/api/web/export/zimmer.:fmt', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM rooms ORDER BY station, zimmer');
  const headers = ['Station','Zimmer','TV-Typ','Seriennummer','Freenet-ID','Gültig bis','Letzte Prüfung','Inaktiv'];
  const dataRows = rows.map((r) => [r.station, r.zimmer, r.tv_typ, r.seriennummer, r.freenet_id,
    r.gueltig_bis, r.letzte_pruefung, r.inaktiv ? 'Ja' : '']);
  if (req.params.fmt === 'csv') return sendeCsv(res, 'KKH-Zimmer.csv', headers, dataRows);
  await sendeXlsx(res, 'KKH-Zimmer.xlsx', [{ name: 'Zimmer',
    columns: headers.map((h, i) => ({ header: h, key: String(i), width: 18 })),
    rows: dataRows }]);
});

router.get('/api/web/export/pruefungen.:fmt', requireWebAuth, async (req, res) => {
  const von = req.query.von || '';
  const bis = req.query.bis || '';
  let sql = `SELECT i.datum, r.station, r.zimmer, i.mitarbeiter,
    i.daten->>'bemerkungen' AS bemerkungen,
    i.daten->'arbeiten' AS arbeiten, i.daten->'punkte' AS punkte
    FROM inspections i LEFT JOIN rooms r ON r.id=i.room_id
    WHERE COALESCE(i.geloescht,FALSE)=FALSE`;
  const werte = [];
  if (von) { werte.push(von); sql += ` AND i.datum>=$${werte.length}`; }
  if (bis) { werte.push(bis); sql += ` AND i.datum<=$${werte.length}`; }
  sql += ' ORDER BY i.datum DESC, r.station, r.zimmer';
  const { rows } = await pool.query(sql, werte);
  const headers = ['Datum','Station','Zimmer','Prüfer','Arbeiten','Bemerkungen'];
  const dataRows = rows.map((r) => [r.datum, r.station, r.zimmer, r.mitarbeiter,
    (r.arbeiten || []).join(', '), r.bemerkungen || '']);
  if (req.params.fmt === 'csv') return sendeCsv(res, 'KKH-Pruefungen.csv', headers, dataRows);
  await sendeXlsx(res, 'KKH-Pruefungen.xlsx', [{ name: 'Prüfungen',
    columns: headers.map((h, i) => ({ header: h, key: String(i), width: i < 2 ? 14 : 20 })),
    rows: dataRows }]);
});

router.get('/api/web/export/stundenzettel.:fmt', requireWebAuth, async (req, res) => {
  const { rows: zRows } = await pool.query('SELECT * FROM stundenzettel ORDER BY zeitraum_start DESC, station');
  const { rows: eRows } = await pool.query('SELECT * FROM zettel_eintraege ORDER BY station, zeitraum_start, mitarbeiter');
  const zHeaders = ['Auftragsnummer','Station','Zeitraum ab','Datum','Stunden','Anfahrt','Techniker'];
  const zData = zRows.map((r) => [r.auftragsnummer, r.station, r.zeitraum_start, r.datum, r.stunden, r.anfahrt, r.techniker]);
  const eHeaders = ['Station','Zeitraum ab','Mitarbeiter','Stunden','Anfahrt'];
  const eData = eRows.map((r) => [r.station, r.zeitraum_start, r.mitarbeiter, r.stunden, r.anfahrt]);
  if (req.params.fmt === 'csv') return sendeCsv(res, 'KKH-Stundenzettel.csv', zHeaders, zData);
  await sendeXlsx(res, 'KKH-Stundenzettel.xlsx', [
    { name: 'Stundenzettel', columns: zHeaders.map((h, i) => ({ header: h, key: String(i), width: 18 })), rows: zData },
    { name: 'Team-Einträge', columns: eHeaders.map((h, i) => ({ header: h, key: String(i), width: 18 })), rows: eData },
  ]);
});

router.get('/api/web/export/abrechnung.:fmt', requireWebAuth, async (req, res) => {
  const von = req.query.von || new Date(Date.now() - 30 * 86400e3).toISOString().slice(0, 10);
  const bis = req.query.bis || new Date().toISOString().slice(0, 10);
  // Prüfungen mit Arbeiten im Zeitraum
  const { rows } = await pool.query(
    `SELECT i.datum, r.station, r.zimmer, i.mitarbeiter AS pruefer,
            elem.value AS material, COUNT(*)::int AS anzahl,
            a.vk_preis, ROUND((a.vk_preis * COUNT(*))::numeric, 2) AS gesamt_vk
     FROM inspections i
     JOIN rooms r ON r.id = i.room_id
     CROSS JOIN LATERAL jsonb_array_elements_text(i.daten->'arbeiten') AS elem(value)
     LEFT JOIN lager_artikel a ON lower(a.bezeichnung)=lower(elem.value) AND a.aktiv=TRUE
     WHERE COALESCE(i.geloescht,FALSE)=FALSE AND i.datum>=$1 AND i.datum<=$2
     GROUP BY i.datum, r.station, r.zimmer, i.mitarbeiter, elem.value, a.vk_preis
     ORDER BY r.station, i.datum, r.zimmer`, [von, bis]);
  // Stundenzettel im Zeitraum
  const { rows: zRows } = await pool.query(
    `SELECT station, auftragsnummer, datum, stunden, anfahrt, techniker
     FROM stundenzettel WHERE zeitraum_start>=$1 AND zeitraum_start<=$2 ORDER BY station`, [von, bis]);
  const headers = ['Datum','Station','Zimmer','Prüfer','Material/Arbeit','Anzahl','VK-Preis','Gesamt VK'];
  const dataRows = rows.map((r) => [r.datum, r.station, r.zimmer, r.pruefer,
    r.material, r.anzahl, r.vk_preis ?? '', r.gesamt_vk ?? '']);
  const zHeaders = ['Station','Auftragsnummer','Datum','Stunden','Anfahrt','Techniker'];
  const zData = zRows.map((r) => [r.station, r.auftragsnummer, r.datum, r.stunden, r.anfahrt, r.techniker]);
  if (req.params.fmt === 'csv') return sendeCsv(res, `KKH-Abrechnung_${von}_${bis}.csv`, headers, dataRows);
  await sendeXlsx(res, `KKH-Abrechnung_${von}_${bis}.xlsx`, [
    { name: 'Material-Abrechnung', columns: headers.map((h, i) => ({ header: h, key: String(i), width: 20 })), rows: dataRows },
    { name: 'Stundenzettel', columns: zHeaders.map((h, i) => ({ header: h, key: String(i), width: 18 })), rows: zData },
  ]);
});

router.get('/api/web/export/lager-artikel.:fmt', requireWebAuth, async (req, res) => {
  const { rows } = await pool.query(
    `SELECT a.bezeichnung, a.artikelnummer, a.kategorie, a.einheit, a.ek_preis, a.vk_preis,
            a.bestand, a.mindestbestand, l.name AS lieferant, a.app_material_name, a.notiz
     FROM lager_artikel a LEFT JOIN lieferanten l ON l.id=a.lieferant_id
     WHERE a.aktiv=TRUE ORDER BY a.kategorie, a.bezeichnung`);
  const headers = ['Bezeichnung','Artikelnummer','Kategorie','Einheit','EK-Preis','VK-Preis','Bestand','Mindestbestand','Lieferant','App-Material','Notiz'];
  const dataRows = rows.map((r) => [r.bezeichnung, r.artikelnummer, r.kategorie, r.einheit,
    r.ek_preis, r.vk_preis, r.bestand, r.mindestbestand, r.lieferant || '', r.app_material_name, r.notiz]);
  if (req.params.fmt === 'csv') return sendeCsv(res, 'KKH-Lager.csv', headers, dataRows);
  await sendeXlsx(res, 'KKH-Lager.xlsx', [{ name: 'Artikel',
    columns: headers.map((h, i) => ({ header: h, key: String(i), width: 20,
      numFmt: (i === 4 || i === 5) ? '#,##0.00 €' : undefined })),
    rows: dataRows }]);
});

router.get('/api/web/export/buchungen.:fmt', requireWebAuth, async (req, res) => {
  const von = req.query.von || '';
  const bis = req.query.bis || '';
  let sql = `SELECT b.zeitpunkt, a.bezeichnung, b.typ, b.menge, a.einheit,
             b.ek_preis, b.grund, b.bezug, b.benutzer
             FROM lager_buchungen b JOIN lager_artikel a ON a.id=b.artikel_id WHERE 1=1`;
  const werte = [];
  if (von) { werte.push(von + 'T00:00:00'); sql += ` AND b.zeitpunkt>=$${werte.length}::timestamptz`; }
  if (bis) { werte.push(bis + 'T23:59:59'); sql += ` AND b.zeitpunkt<=$${werte.length}::timestamptz`; }
  sql += ' ORDER BY b.zeitpunkt DESC';
  const { rows } = await pool.query(sql, werte);
  const headers = ['Zeitpunkt','Artikel','Typ','Menge','Einheit','EK-Preis','Grund','Bezug','Benutzer'];
  const dataRows = rows.map((r) => [r.zeitpunkt.toISOString().slice(0, 16).replace('T', ' '),
    r.bezeichnung, r.typ, r.menge, r.einheit, r.ek_preis ?? '', r.grund, r.bezug, r.benutzer]);
  if (req.params.fmt === 'csv') return sendeCsv(res, 'KKH-Buchungen.csv', headers, dataRows);
  await sendeXlsx(res, 'KKH-Buchungen.xlsx', [{ name: 'Buchungen',
    columns: headers.map((h, i) => ({ header: h, key: String(i), width: 20 })),
    rows: dataRows }]);
});

// XLSX-Export Zeiterfassung
router.get('/api/web/export/zeiterfassung.xlsx', requireWebAuth, async (req, res) => {
  const von = req.query.von || '';
  const bis = req.query.bis || '';
  let sql = `SELECT z.datum, z.mitarbeiter, z.von, z.bis, z.pause_min, b.name AS baustelle, z.taetigkeit, z.bemerkung
             FROM zeiterfassung z LEFT JOIN baustellen b ON b.id=z.baustelle_id WHERE 1=1`;
  const werte = [];
  if (von) { werte.push(von); sql += ` AND z.datum>=$${werte.length}`; }
  if (bis) { werte.push(bis); sql += ` AND z.datum<=$${werte.length}`; }
  sql += ' ORDER BY z.datum, z.mitarbeiter';
  const { rows } = await pool.query(sql, werte);
  const headers = ['Datum','Mitarbeiter','Von','Bis','Pause (Min.)','Stunden','Baustelle','Tätigkeit','Bemerkung'];
  const dataRows = rows.map((r) => {
    let std = '';
    if (r.von && r.bis && /^\d{2}:\d{2}/.test(r.von) && /^\d{2}:\d{2}/.test(r.bis)) {
      const [hV, mV] = r.von.split(':').map(Number);
      const [hB, mB] = r.bis.split(':').map(Number);
      const min = (hB * 60 + mB) - (hV * 60 + mV) - (r.pause_min || 0);
      std = min > 0 ? (min / 60).toFixed(2) : '0';
    }
    return [r.datum, r.mitarbeiter, r.von, r.bis, r.pause_min, std, r.baustelle || '', r.taetigkeit, r.bemerkung];
  });
  await sendeXlsx(res, `Zeiterfassung_${von || 'gesamt'}_${bis || ''}.xlsx`, [{
    name: 'Zeiterfassung',
    columns: headers.map((h, i) => ({ header: h, key: String(i), width: 16 })),
    rows: dataRows,
  }]);
});

// ---------- KI-Fotoerkennung (Web-API) ----------
// Der eigentliche KI-Service läuft als eigener Container (server/ki/),
// nur intern erreichbar unter http://ki:8100.
const KI_URL = process.env.KI_URL || 'http://ki:8100';

router.get('/api/web/ki/analysen', requireWebAuth, async (req, res) => {
  const bedingungen = [];
  const params = [];
  if (req.query.status) { params.push(String(req.query.status)); bedingungen.push(`status=$${params.length}`); }
  if (req.query.room) { params.push(String(req.query.room)); bedingungen.push(`room_id=$${params.length}`); }
  const where = bedingungen.length ? `WHERE ${bedingungen.join(' AND ')}` : '';
  const { rows } = await pool.query(
    `SELECT id, pfad, room_id, bildtyp, felder, abgleich, status, modell_version,
            fehler, erstellt_am, analysiert_am
     FROM foto_analysen ${where}
     ORDER BY analysiert_am DESC NULLS LAST, id DESC LIMIT 500`, params);
  // OCR-Rohtext nicht mitschicken (groß, im Web nicht gebraucht)
  res.json({
    analysen: rows.map((r) => {
      const felder = { ...(r.felder || {}) };
      delete felder._ocr;
      return { ...r, felder };
    }),
  });
});

// Monteur/Admin bestätigt oder korrigiert eine Analyse.
// Body: { entscheidungen: { seriennummer: { wert, stammdatenUebernehmen }, ... } }
// Jede Entscheidung wird als Trainingslabel gespeichert; auf Wunsch werden
// die Zimmer-Stammdaten direkt korrigiert. Gemeinsame Logik für Web + App.
async function kiBestaetigen(analyseId, entscheidungen) {
  const { rows } = await pool.query('SELECT * FROM foto_analysen WHERE id=$1', [analyseId]);
  if (!rows.length) return { error: 'Analyse nicht gefunden', status: 404 };
  const analyse = rows[0];
  const spalten = { seriennummer: 'seriennummer', freenet_id: 'freenet_id', gueltig_bis: 'gueltig_bis', tv_typ: 'tv_typ' };

  for (const [feld, e] of Object.entries(entscheidungen || {})) {
    const wert = String(e.wert || '').trim();
    if (!wert && feld !== 'bildtyp') continue;
    await pool.query(
      `INSERT INTO ki_labels (pfad, feld, wert, quelle) VALUES ($1,$2,$3,'web')
       ON CONFLICT (pfad, feld) DO UPDATE SET wert=EXCLUDED.wert, quelle='web', erstellt_am=now()`,
      [analyse.pfad, feld, wert]);
    if (e.stammdatenUebernehmen && spalten[feld] && analyse.room_id) {
      await pool.query(
        `UPDATE rooms SET ${spalten[feld]}=$1, updated_at=$2 WHERE id=$3`,
        [wert, new Date().toISOString(), analyse.room_id]);
    }
  }
  // Analyse als erledigt markieren (bestätigt = Übereinstimmung hergestellt)
  await pool.query(`UPDATE foto_analysen SET status='uebereinstimmung' WHERE id=$1`, [analyseId]);
  return { ok: true };
}

router.post('/api/web/ki/analysen/:id/bestaetigen', requireWebAuth, express.json(), async (req, res) => {
  const ergebnis = await kiBestaetigen(req.params.id, req.body.entscheidungen);
  if (ergebnis.error) return res.status(ergebnis.status).json({ error: ergebnis.error });
  res.json(ergebnis);
});

// Foto erneut analysieren lassen (z. B. nach einem Training)
router.post('/api/web/ki/analysen/:id/neu', requireWebAuth, async (req, res) => {
  const r = await pool.query(
    `UPDATE foto_analysen SET status='wartet', fehler='' WHERE id=$1 RETURNING id`,
    [req.params.id]);
  if (!r.rowCount) return res.status(404).json({ error: 'Analyse nicht gefunden' });
  res.json({ ok: true });
});

// Status des KI-Service (Warteschlange, Modelle) durchreichen
router.get('/api/web/ki/status', requireWebAuth, async (req, res) => {
  try {
    const antwort = await fetch(`${KI_URL}/status`);
    res.json(await antwort.json());
  } catch {
    res.json({ offline: true });
  }
});

// Training des KI-Service manuell anstoßen
router.post('/api/web/ki/train', requireWebAuth, async (req, res) => {
  try {
    const antwort = await fetch(`${KI_URL}/train`, { method: 'POST' });
    res.json(await antwort.json());
  } catch (e) {
    res.status(502).json({ error: `KI-Service nicht erreichbar: ${e.message}` });
  }
});

// ---------- KI-Fotoerkennung (App-API, ab v2.0-Beta) ----------
// Gleiche Auswertung + Trainings-Entscheidung wie im Web-Panel, nur per
// X-Api-Key statt Session – so kann direkt vor Ort auf dem Gerät bestätigt
// oder korrigiert werden, statt erst am PC im Web-Panel.
router.get('/api/sync/ki/analysen', requireApiKey, async (req, res) => {
  const bedingungen = [];
  const params = [];
  if (req.query.status) { params.push(String(req.query.status)); bedingungen.push(`status=$${params.length}`); }
  if (req.query.room) { params.push(String(req.query.room)); bedingungen.push(`room_id=$${params.length}`); }
  const where = bedingungen.length ? `WHERE ${bedingungen.join(' AND ')}` : '';
  const { rows } = await pool.query(
    `SELECT id, pfad, room_id, bildtyp, felder, abgleich, status, modell_version,
            fehler, erstellt_am, analysiert_am
     FROM foto_analysen ${where}
     ORDER BY analysiert_am DESC NULLS LAST, id DESC LIMIT 200`, params);
  res.json({
    analysen: rows.map((r) => {
      const felder = { ...(r.felder || {}) };
      delete felder._ocr; // OCR-Rohtext ist groß, in der App nicht gebraucht
      return {
        id: r.id, pfad: r.pfad, roomId: r.room_id, bildtyp: r.bildtyp,
        felder, abgleich: r.abgleich || {}, status: r.status,
        modellVersion: r.modell_version, fehler: r.fehler,
        erstelltAm: r.erstellt_am, analysiertAm: r.analysiert_am,
      };
    }),
  });
});

router.post('/api/sync/ki/analysen/:id/bestaetigen', requireApiKey, express.json(), async (req, res) => {
  const ergebnis = await kiBestaetigen(req.params.id, req.body.entscheidungen);
  if (ergebnis.error) return res.status(ergebnis.status).json({ error: ergebnis.error });
  res.json(ergebnis);
});

router.post('/api/sync/ki/analysen/:id/neu', requireApiKey, async (req, res) => {
  const r = await pool.query(
    `UPDATE foto_analysen SET status='wartet', fehler='' WHERE id=$1 RETURNING id`,
    [req.params.id]);
  if (!r.rowCount) return res.status(404).json({ error: 'Analyse nicht gefunden' });
  res.json({ ok: true });
});

router.get('/api/sync/ki/status', requireApiKey, async (req, res) => {
  try {
    const antwort = await fetch(`${KI_URL}/status`);
    res.json(await antwort.json());
  } catch {
    res.json({ offline: true });
  }
});

// Einzelnes Foto lesen (für die KI-Prüfung in der App – ausdrücklich nur
// dieser eine, gezielt abgerufene Pfad, kein Massen-Download aller Fotos).
router.get('/api/sync/file', requireApiKey, (req, res) => {
  const rel = String(req.query.path || '');
  if (rel.includes('_signaturen')) return res.status(403).json({ error: 'Zugriff auf Signaturen nicht erlaubt' });
  const ziel = safeFilePath(rel);
  if (!ziel || !fs.existsSync(ziel)) return res.status(404).json({ error: 'Datei nicht gefunden' });
  res.sendFile(ziel);
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
  // Signaturen dürfen NICHT über die Web-API ausgeliefert werden
  if (rel.includes('_signaturen')) return res.status(403).json({ error: 'Zugriff auf Signaturen nicht erlaubt' });
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

// ---------- Statische Weboberfläche KKH ----------
router.use(express.static(path.join(__dirname, '..', 'public')));

// ---------- Excero-Webapp: eigener Pfad /excero/ ----------
const EXCERO_PATH = '/excero';
const exceroPublic = path.join(__dirname, '..', 'public-excero');

// Login für /excero/ – setzt denselben kkh_session Cookie
exceroRouter.post('/api/login', express.json(), async (req, res) => {
  const { username, password } = req.body || {};
  if (!username || !password) return res.status(400).json({ error: 'Benutzername und Passwort nötig' });
  try {
    const u = await findUser(username);
    if (!u || !verifyPassword(password, u.password_hash, u.salt)) {
      return res.status(401).json({ error: 'Ungültige Anmeldedaten' });
    }
    const token = signSession(u.username, Date.now() + 12 * 60 * 60 * 1000);
    res.setHeader('Set-Cookie',
      `kkh_session=${encodeURIComponent(token)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=43200`);
    res.json({ ok: true, username: u.username });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

exceroRouter.post('/api/logout', (req, res) => {
  res.setHeader('Set-Cookie', `kkh_session=; Path=/; HttpOnly; Max-Age=0`);
  res.json({ ok: true });
});

exceroRouter.get('/api/me', (req, res) => {
  const username = checkSession(parseCookies(req).kkh_session);
  if (username) return res.json({ username });
  res.status(401).json({ error: 'Nicht angemeldet' });
});

// Excero-Frontend (SPA) – statische Dateien + Fallback auf index.html
if (require('fs').existsSync(exceroPublic)) {
  exceroRouter.use(express.static(exceroPublic));
  exceroRouter.get('*', (req, res) => {
    const idx = path.join(exceroPublic, 'index.html');
    if (require('fs').existsSync(idx)) res.sendFile(idx);
    else res.status(404).send('Excero-Frontend noch nicht eingerichtet');
  });
}

app.use(BASE_PATH || '/', router);
app.use(EXCERO_PATH, exceroRouter);
if (BASE_PATH) app.get(BASE_PATH, (req, res) => res.redirect(`${BASE_PATH}/`));
app.get(EXCERO_PATH, (req, res) => res.redirect(`${EXCERO_PATH}/`));
app.get('/', (req, res) => res.redirect(`${BASE_PATH}/`));

init().then(async () => {
  await seedFirstUser();
  app.listen(PORT, () => {
    console.log(`KKH-Server läuft auf Port ${PORT} unter ${BASE_PATH}/`);
    console.log(`Excero-Webapp erreichbar unter ${EXCERO_PATH}/`);
  });
}).catch((e) => {
  console.error('Start fehlgeschlagen:', e);
  process.exit(1);
});
