'use strict';
const express = require('express');
const router = express.Router();
const { pool } = require('../db');

// ── Baustellen ────────────────────────────────────────────────────────
router.get('/baustellen', async (req, res) => {
  const firmaId = req.query.firma_id ? Number(req.query.firma_id) : null;
  let sql = `SELECT b.*, f.name AS firma_name, k.name AS kunde_name
             FROM baustellen b
             LEFT JOIN firmen f ON f.id = b.firma_id
             LEFT JOIN kunden k ON k.id = b.kunde_id
             WHERE 1=1`;
  const werte = [];
  if (firmaId) { werte.push(firmaId); sql += ` AND (b.firma_id=$${werte.length} OR b.firma_id IS NULL)`; }
  sql += ' ORDER BY b.status, b.name';
  const { rows } = await pool.query(sql, werte);
  res.json({ baustellen: rows });
});

router.post('/baustellen', express.json(), async (req, res) => {
  const b = req.body || {};
  if (!String(b.name || '').trim()) return res.status(400).json({ error: 'Name ist Pflicht' });
  const { rows } = await pool.query(
    `INSERT INTO baustellen (firma_id, kunde_id, name, adresse, status, beginn, ende, stundensatz, notiz)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
    [b.firma_id || null, b.kunde_id || null, b.name.trim(), b.adresse || '',
     b.status || 'aktiv', b.beginn || '', b.ende || '', b.stundensatz || null, b.notiz || '']);
  res.json({ ok: true, baustelle: rows[0] });
});

router.patch('/baustellen/:id', express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const b = req.body || {};
  const felder = [], werte = [];
  const add = (k, v) => { felder.push(`${k}=$${felder.length + 1}`); werte.push(v); };
  if (b.name !== undefined) add('name', String(b.name).trim());
  if (b.firma_id !== undefined) add('firma_id', b.firma_id || null);
  if (b.kunde_id !== undefined) add('kunde_id', b.kunde_id || null);
  if (b.adresse !== undefined) add('adresse', String(b.adresse));
  if (b.status !== undefined) add('status', String(b.status));
  if (b.beginn !== undefined) add('beginn', String(b.beginn));
  if (b.ende !== undefined) add('ende', String(b.ende));
  if (b.stundensatz !== undefined) add('stundensatz', b.stundensatz || null);
  if (b.notiz !== undefined) add('notiz', String(b.notiz));
  if (!felder.length) return res.status(400).json({ error: 'Nichts zu aktualisieren' });
  werte.push(id);
  await pool.query(`UPDATE baustellen SET ${felder.join(',')} WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});

router.delete('/baustellen/:id', async (req, res) => {
  await pool.query('UPDATE baustellen SET status=$1 WHERE id=$2', ['abgeschlossen', Number(req.params.id)]);
  res.json({ ok: true });
});

// ── Kunden ────────────────────────────────────────────────────────────
router.get('/kunden', async (req, res) => {
  const { rows } = await pool.query('SELECT * FROM kunden WHERE aktiv=TRUE ORDER BY name');
  res.json({ kunden: rows });
});

router.post('/kunden', express.json(), async (req, res) => {
  const b = req.body || {};
  if (!String(b.name || '').trim()) return res.status(400).json({ error: 'Name ist Pflicht' });
  const { rows } = await pool.query(
    `INSERT INTO kunden (firma_id, name, anrede, adresse, email, telefon, steuernummer, ust_id, notiz)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
    [b.firma_id || null, b.name.trim(), b.anrede || '', b.adresse || '',
     b.email || '', b.telefon || '', b.steuernummer || '', b.ust_id || '', b.notiz || '']);
  res.json({ ok: true, kunde: rows[0] });
});

router.patch('/kunden/:id', express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const b = req.body || {};
  const felder = [], werte = [];
  const add = (k, v) => { felder.push(`${k}=$${felder.length + 1}`); werte.push(v); };
  if (b.name !== undefined) add('name', String(b.name).trim());
  if (b.anrede !== undefined) add('anrede', String(b.anrede));
  if (b.adresse !== undefined) add('adresse', String(b.adresse));
  if (b.email !== undefined) add('email', String(b.email));
  if (b.telefon !== undefined) add('telefon', String(b.telefon));
  if (b.notiz !== undefined) add('notiz', String(b.notiz));
  if (b.aktiv !== undefined) add('aktiv', !!b.aktiv);
  if (!felder.length) return res.status(400).json({ error: 'Nichts zu aktualisieren' });
  werte.push(id);
  await pool.query(`UPDATE kunden SET ${felder.join(',')} WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});

module.exports = router;
