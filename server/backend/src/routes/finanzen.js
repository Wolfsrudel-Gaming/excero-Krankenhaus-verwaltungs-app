'use strict';
const express = require('express');
const router = express.Router();
const { pool } = require('../db');

// ── Ausgaben ────────────────────────────────────────────────────────────
router.get('/ausgaben', async (req, res) => {
  const firmaId = req.query.firma_id ? Number(req.query.firma_id) : null;
  const baustelleId = req.query.baustelle_id ? Number(req.query.baustelle_id) : null;
  const von = req.query.von || '';
  const bis = req.query.bis || '';
  let sql = `SELECT a.*, b.name AS baustelle_name
             FROM ausgaben a LEFT JOIN baustellen b ON b.id = a.baustelle_id WHERE 1=1`;
  const werte = [];
  if (firmaId) { werte.push(firmaId); sql += ` AND a.firma_id=$${werte.length}`; }
  if (baustelleId) { werte.push(baustelleId); sql += ` AND a.baustelle_id=$${werte.length}`; }
  if (von) { werte.push(von); sql += ` AND a.datum>=$${werte.length}`; }
  if (bis) { werte.push(bis); sql += ` AND a.datum<=$${werte.length}`; }
  sql += ' ORDER BY a.datum DESC';
  const { rows } = await pool.query(sql, werte);
  res.json({ ausgaben: rows });
});

router.post('/ausgaben', express.json(), async (req, res) => {
  const b = req.body || {};
  if (!b.datum || !b.bezeichnung || !b.betrag) {
    return res.status(400).json({ error: 'datum, bezeichnung und betrag sind Pflicht' });
  }
  const { rows } = await pool.query(
    `INSERT INTO ausgaben (firma_id, baustelle_id, datum, kategorie, bezeichnung, betrag, beleg_notiz)
     VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *`,
    [b.firma_id || null, b.baustelle_id || null, b.datum,
     b.kategorie || '', b.bezeichnung, Number(b.betrag), b.beleg_notiz || '']);
  res.json({ ok: true, ausgabe: rows[0] });
});

router.patch('/ausgaben/:id', express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const b = req.body || {};
  const felder = [], werte = [];
  const add = (k, v) => { felder.push(`${k}=$${felder.length + 1}`); werte.push(v); };
  if (b.datum !== undefined) add('datum', String(b.datum));
  if (b.kategorie !== undefined) add('kategorie', String(b.kategorie));
  if (b.bezeichnung !== undefined) add('bezeichnung', String(b.bezeichnung));
  if (b.betrag !== undefined) add('betrag', Number(b.betrag));
  if (b.beleg_notiz !== undefined) add('beleg_notiz', String(b.beleg_notiz));
  if (b.firma_id !== undefined) add('firma_id', b.firma_id || null);
  if (b.baustelle_id !== undefined) add('baustelle_id', b.baustelle_id || null);
  if (!felder.length) return res.status(400).json({ error: 'Nichts zu aktualisieren' });
  werte.push(id);
  await pool.query(`UPDATE ausgaben SET ${felder.join(',')} WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});

router.delete('/ausgaben/:id', async (req, res) => {
  await pool.query('DELETE FROM ausgaben WHERE id=$1', [Number(req.params.id)]);
  res.json({ ok: true });
});

// ── GuV-Auswertung ───────────────────────────────────────────────────────
router.get('/finanzen/guv', async (req, res) => {
  const von = req.query.von || new Date(Date.now() - 30 * 86400e3).toISOString().slice(0, 10);
  const bis = req.query.bis || new Date().toISOString().slice(0, 10);
  const firmaId = req.query.firma_id ? Number(req.query.firma_id) : null;
  const baustelleId = req.query.baustelle_id ? Number(req.query.baustelle_id) : null;

  const wFirma = (col, idx) => firmaId ? ` AND ${col}=$${idx}` : '';
  const wBau   = (col, idx) => baustelleId ? ` AND ${col}=$${idx}` : '';

  const baseWerte = [von, bis];
  if (firmaId) baseWerte.push(firmaId);
  if (baustelleId) baseWerte.push(baustelleId);
  const fi = firmaId ? baseWerte.indexOf(firmaId) + 1 : 0;
  const bi = baustelleId ? baseWerte.indexOf(baustelleId) + 1 : 0;

  // Einnahmen (bezahlte Rechnungen)
  const [einQ, ausQ, lagerQ, zeitQ] = await Promise.all([
    pool.query(
      `SELECT COALESCE(SUM(brutto), 0) AS einnahmen FROM rechnungen
       WHERE bezahlt_am::date >= $1 AND bezahlt_am::date <= $2
         AND status = 'bezahlt'
         ${firmaId ? `AND firma_id=$${fi}` : ''}`,
      firmaId ? [von, bis, firmaId] : [von, bis]),
    pool.query(
      `SELECT COALESCE(SUM(betrag), 0) AS ausgaben FROM ausgaben
       WHERE datum >= $1 AND datum <= $2
         ${firmaId ? `AND firma_id=$${fi}` : ''}
         ${baustelleId ? `AND baustelle_id=$${bi}` : ''}`,
      baseWerte.slice(0, firmaId || baustelleId ? 2 + (firmaId ? 1 : 0) + (baustelleId ? 1 : 0) : 2)),
    pool.query(
      `SELECT COALESCE(SUM(b.menge * b.ek_preis), 0) AS materialkosten
       FROM lager_buchungen b
       WHERE b.zeitpunkt::date >= $1 AND b.zeitpunkt::date <= $2
         AND b.typ = 'eingang' AND b.ek_preis IS NOT NULL`,
      [von, bis]),
    pool.query(
      `SELECT COALESCE(SUM(
        CASE WHEN z.von ~ '^[0-9]{2}:[0-9]{2}$' AND z.bis ~ '^[0-9]{2}:[0-9]{2}$'
        THEN (EXTRACT(HOUR FROM (z.bis::time - z.von::time)) * 60
            + EXTRACT(MINUTE FROM (z.bis::time - z.von::time)) - z.pause_min) / 60.0
        ELSE 0 END), 0) AS stunden
       FROM zeiterfassung z WHERE z.datum >= $1 AND z.datum <= $2
         ${baustelleId ? `AND z.baustelle_id=$${bi}` : ''}`,
      baustelleId ? [von, bis, baustelleId] : [von, bis]),
  ]);

  const einnahmen = Number(einQ.rows[0].einnahmen);
  const ausgaben  = Number(ausQ.rows[0].ausgaben);
  const material  = Number(lagerQ.rows[0].materialkosten);
  const stunden   = Number(zeitQ.rows[0].stunden).toFixed(2);

  res.json({
    von, bis,
    einnahmen,
    ausgaben,
    materialkosten: material,
    gesamtkosten: ausgaben + material,
    deckungsbeitrag: einnahmen - ausgaben - material,
    stunden: Number(stunden),
  });
});

// Monatsübersicht
router.get('/finanzen/monate', async (req, res) => {
  const firmaId = req.query.firma_id ? Number(req.query.firma_id) : null;
  const { rows } = await pool.query(
    `SELECT
       TO_CHAR(bezahlt_am::date, 'YYYY-MM') AS monat,
       COUNT(*)::int AS rechnungen,
       COALESCE(SUM(brutto), 0) AS einnahmen
     FROM rechnungen
     WHERE status = 'bezahlt' AND bezahlt_am IS NOT NULL
       ${firmaId ? 'AND firma_id=$1' : ''}
     GROUP BY monat ORDER BY monat DESC LIMIT 24`,
    firmaId ? [firmaId] : []);
  res.json({ monate: rows });
});

module.exports = router;
