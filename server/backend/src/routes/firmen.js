'use strict';
/**
 * Firmen / Mandanten + globale Einstellungen (SMTP, HiDrive)
 */
const express = require('express');
const router = express.Router();
const { pool } = require('../db');

// ── Firmen ──────────────────────────────────────────────────────────────
router.get('/firmen', async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM firmen ORDER BY id');
  res.json({ firmen: rows });
});

router.post('/firmen', express.json(), async (req, res) => {
  const b = req.body || {};
  if (!String(b.name || '').trim()) return res.status(400).json({ error: 'Name ist Pflicht' });
  const { rows } = await pool.query(
    `INSERT INTO firmen (name, rechtsform, adresse, steuernummer, ust_id, besteuerung, ust_satz,
       iban, bic, bank_name, stundensatz, rechnungs_prefix, rechnungs_fusstext,
       email, telefon, webseite, aktiv)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17) RETURNING *`,
    [b.name, b.rechtsform||'', b.adresse||'', b.steuernummer||'', b.ust_id||'',
     b.besteuerung||'regel', Number(b.ust_satz)||19,
     b.iban||'', b.bic||'', b.bank_name||'', b.stundensatz||null,
     b.rechnungs_prefix||'RE', b.rechnungs_fusstext||'',
     b.email||'', b.telefon||'', b.webseite||'', b.aktiv !== false]);
  res.json({ ok: true, firma: rows[0] });
});

router.patch('/firmen/:id', express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const b = req.body || {};
  const felder = [], werte = [];
  const add = (k, v) => { felder.push(`${k}=$${felder.length + 1}`); werte.push(v); };
  const str = (v) => v !== undefined ? String(v) : undefined;
  if (str(b.name) !== undefined) add('name', str(b.name).trim());
  if (str(b.rechtsform) !== undefined) add('rechtsform', str(b.rechtsform));
  if (str(b.adresse) !== undefined) add('adresse', str(b.adresse));
  if (str(b.steuernummer) !== undefined) add('steuernummer', str(b.steuernummer));
  if (str(b.ust_id) !== undefined) add('ust_id', str(b.ust_id));
  if (str(b.besteuerung) !== undefined) add('besteuerung', str(b.besteuerung));
  if (b.ust_satz !== undefined) add('ust_satz', Number(b.ust_satz));
  if (str(b.iban) !== undefined) add('iban', str(b.iban));
  if (str(b.bic) !== undefined) add('bic', str(b.bic));
  if (str(b.bank_name) !== undefined) add('bank_name', str(b.bank_name));
  if (b.stundensatz !== undefined) add('stundensatz', b.stundensatz || null);
  if (str(b.rechnungs_prefix) !== undefined) add('rechnungs_prefix', str(b.rechnungs_prefix));
  if (str(b.rechnungs_fusstext) !== undefined) add('rechnungs_fusstext', str(b.rechnungs_fusstext));
  if (str(b.email) !== undefined) add('email', str(b.email));
  if (str(b.telefon) !== undefined) add('telefon', str(b.telefon));
  if (str(b.webseite) !== undefined) add('webseite', str(b.webseite));
  if (b.logo_pfad !== undefined) add('logo_pfad', str(b.logo_pfad));
  if (b.aktiv !== undefined) add('aktiv', !!b.aktiv);
  if (!felder.length) return res.status(400).json({ error: 'Nichts zu aktualisieren' });
  werte.push(id);
  await pool.query(`UPDATE firmen SET ${felder.join(',')} WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});

// ── Einstellungen ────────────────────────────────────────────────────────
const SENSITIVE_KEYS = ['smtp', 'hidrive'];

router.get('/einstellungen', async (req, res) => {
  const { rows } = await pool.query('SELECT key, wert FROM einstellungen ORDER BY key');
  const out = {};
  for (const r of rows) {
    if (r.wert === null) { out[r.key] = null; continue; }
    // Passwörter maskieren
    const v = { ...(r.wert || {}) };
    if (typeof v.passwort === 'string') v.passwort = v.passwort ? '••••••••' : '';
    if (typeof v.password === 'string') v.password = v.password ? '••••••••' : '';
    out[r.key] = v;
  }
  res.json({ einstellungen: out });
});

router.put('/einstellungen/:key', express.json(), async (req, res) => {
  const key = req.params.key;
  if (!SENSITIVE_KEYS.includes(key)) return res.status(400).json({ error: 'Unbekannter Schlüssel' });
  // Bei Passwort "••••••••" (Platzhalter) alten Wert beibehalten
  const { rows: alt } = await pool.query('SELECT wert FROM einstellungen WHERE key=$1', [key]);
  const altWert = alt[0]?.wert || {};
  const neu = req.body || {};
  if (neu.passwort === '••••••••') neu.passwort = altWert.passwort || '';
  if (neu.password === '••••••••') neu.password = altWert.password || '';
  await pool.query(
    `INSERT INTO einstellungen (key, wert) VALUES ($1, $2)
     ON CONFLICT (key) DO UPDATE SET wert = EXCLUDED.wert`,
    [key, JSON.stringify(neu)]);
  res.json({ ok: true });
});

module.exports = router;
