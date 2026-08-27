'use strict';
const express = require('express');
const router = express.Router();
const { pool } = require('../db');

// ── Zeiterfassung ────────────────────────────────────────────────────
router.get('/zeiterfassung', async (req, res) => {
  const von = req.query.von || '';
  const bis = req.query.bis || '';
  const mitarbeiter = req.query.mitarbeiter || '';
  const baustelleId = req.query.baustelle_id ? Number(req.query.baustelle_id) : null;
  const limit = Math.min(Number(req.query.limit || 500), 5000);
  let sql = `SELECT z.*, b.name AS baustelle_name
             FROM zeiterfassung z
             LEFT JOIN baustellen b ON b.id = z.baustelle_id
             WHERE 1=1`;
  const werte = [];
  if (von) { werte.push(von); sql += ` AND z.datum >= $${werte.length}`; }
  if (bis) { werte.push(bis); sql += ` AND z.datum <= $${werte.length}`; }
  if (mitarbeiter) { werte.push(mitarbeiter); sql += ` AND z.mitarbeiter = $${werte.length}`; }
  if (baustelleId) { werte.push(baustelleId); sql += ` AND z.baustelle_id = $${werte.length}`; }
  sql += ` ORDER BY z.datum DESC, z.mitarbeiter LIMIT $${werte.length + 1}`;
  werte.push(limit);
  const { rows } = await pool.query(sql, werte);
  res.json({ eintraege: rows });
});

router.post('/zeiterfassung', express.json(), async (req, res) => {
  const b = req.body || {};
  if (!b.mitarbeiter || !b.datum) return res.status(400).json({ error: 'mitarbeiter + datum nötig' });
  const { rows } = await pool.query(
    `INSERT INTO zeiterfassung (mitarbeiter, datum, von, bis, pause_min, baustelle_id, taetigkeit, bemerkung)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
    [b.mitarbeiter, b.datum, b.von || '', b.bis || '', Number(b.pause_min) || 0,
     b.baustelle_id || null, b.taetigkeit || '', b.bemerkung || '']);
  res.json({ ok: true, eintrag: rows[0] });
});

router.patch('/zeiterfassung/:id', express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const b = req.body || {};
  const felder = [], werte = [];
  const add = (k, v) => { felder.push(`${k}=$${felder.length + 1}`); werte.push(v); };
  if (b.mitarbeiter !== undefined) add('mitarbeiter', String(b.mitarbeiter));
  if (b.datum !== undefined) add('datum', String(b.datum));
  if (b.von !== undefined) add('von', String(b.von));
  if (b.bis !== undefined) add('bis', String(b.bis));
  if (b.pause_min !== undefined) add('pause_min', Number(b.pause_min) || 0);
  if (b.baustelle_id !== undefined) add('baustelle_id', b.baustelle_id || null);
  if (b.taetigkeit !== undefined) add('taetigkeit', String(b.taetigkeit));
  if (b.bemerkung !== undefined) add('bemerkung', String(b.bemerkung));
  if (!felder.length) return res.status(400).json({ error: 'Nichts zu aktualisieren' });
  werte.push(id);
  await pool.query(`UPDATE zeiterfassung SET ${felder.join(',')} WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});

router.delete('/zeiterfassung/:id', async (req, res) => {
  await pool.query('DELETE FROM zeiterfassung WHERE id=$1', [Number(req.params.id)]);
  res.json({ ok: true });
});

// Auswertung: Stunden je Mitarbeiter + Baustelle
router.get('/zeiterfassung/auswertung', async (req, res) => {
  const von = req.query.von || new Date(Date.now() - 30 * 86400e3).toISOString().slice(0, 10);
  const bis = req.query.bis || new Date().toISOString().slice(0, 10);
  const { rows } = await pool.query(`
    SELECT z.mitarbeiter, b.name AS baustelle,
           COUNT(*)::int AS tage,
           SUM(
             CASE WHEN z.von ~ '^[0-9]{2}:[0-9]{2}$' AND z.bis ~ '^[0-9]{2}:[0-9]{2}$'
             THEN (EXTRACT(HOUR FROM (z.bis::time - z.von::time)) * 60
                 + EXTRACT(MINUTE FROM (z.bis::time - z.von::time))
                 - z.pause_min) / 60.0
             ELSE 0 END
           ) AS stunden
    FROM zeiterfassung z
    LEFT JOIN baustellen b ON b.id = z.baustelle_id
    WHERE z.datum >= $1 AND z.datum <= $2
    GROUP BY z.mitarbeiter, b.name
    ORDER BY z.mitarbeiter, b.name`, [von, bis]);
  res.json({ auswertung: rows, von, bis });
});

module.exports = router;
