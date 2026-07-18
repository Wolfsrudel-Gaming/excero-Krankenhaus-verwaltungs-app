'use strict';
const express = require('express');
const router = express.Router();
const { pool } = require('../db');

// ── Nächste Rechnungsnummer ──────────────────────────────────────────
async function naechsteNummer(firmaId) {
  const jahr = new Date().getFullYear();
  const { rows: f } = await pool.query('SELECT rechnungs_prefix FROM firmen WHERE id=$1', [firmaId]);
  const prefix = f[0]?.rechnungs_prefix || 'RE';
  const { rows } = await pool.query(
    `SELECT nummer FROM rechnungen WHERE firma_id=$1 AND nummer LIKE $2 ORDER BY nummer DESC LIMIT 1`,
    [firmaId, `${prefix}-${jahr}-%`]);
  const lfd = rows.length ? Number(rows[0].nummer.split('-').pop()) + 1 : 1;
  return `${prefix}-${jahr}-${String(lfd).padStart(4, '0')}`;
}

// ── Rechnungen ────────────────────────────────────────────────────────
router.get('/rechnungen', async (req, res) => {
  const firmaId = req.query.firma_id ? Number(req.query.firma_id) : null;
  const status = req.query.status || '';
  const von = req.query.von || '';
  const bis = req.query.bis || '';
  let sql = `SELECT r.*, f.name AS firma_name, b.name AS baustelle_name
             FROM rechnungen r
             LEFT JOIN firmen f ON f.id = r.firma_id
             LEFT JOIN baustellen b ON b.id = r.baustelle_id
             WHERE 1=1`;
  const werte = [];
  if (firmaId) { werte.push(firmaId); sql += ` AND r.firma_id=$${werte.length}`; }
  if (status) { werte.push(status); sql += ` AND r.status=$${werte.length}`; }
  if (von) { werte.push(von); sql += ` AND r.datum>=$${werte.length}`; }
  if (bis) { werte.push(bis); sql += ` AND r.datum<=$${werte.length}`; }
  sql += ' ORDER BY r.datum DESC, r.nummer DESC';
  const { rows } = await pool.query(sql, werte);
  res.json({ rechnungen: rows });
});

router.get('/rechnungen/next-nr', async (req, res) => {
  const firmaId = Number(req.query.firma_id);
  if (!firmaId) return res.status(400).json({ error: 'firma_id nötig' });
  res.json({ nummer: await naechsteNummer(firmaId) });
});

router.get('/rechnungen/:id', async (req, res) => {
  const { rows: r } = await pool.query(
    `SELECT r.*, f.name AS firma_name, f.adresse AS firma_adresse, f.email AS firma_email,
            f.telefon AS firma_telefon, f.steuernummer, f.ust_id, f.besteuerung, f.ust_satz,
            f.iban, f.bic, f.bank_name, f.rechnungs_fusstext, f.logo_pfad
     FROM rechnungen r LEFT JOIN firmen f ON f.id=r.firma_id WHERE r.id=$1`,
    [Number(req.params.id)]);
  if (!r.length) return res.status(404).json({ error: 'Rechnung nicht gefunden' });
  const { rows: pos } = await pool.query(
    'SELECT * FROM rechnung_positionen WHERE rechnung_id=$1 ORDER BY pos',
    [Number(req.params.id)]);
  res.json({ rechnung: r[0], positionen: pos });
});

router.post('/rechnungen', express.json(), async (req, res) => {
  const b = req.body || {};
  if (!b.firma_id) return res.status(400).json({ error: 'firma_id ist Pflicht' });
  const nummer = b.nummer || await naechsteNummer(b.firma_id);
  const { rows } = await pool.query(
    `INSERT INTO rechnungen (firma_id, baustelle_id, nummer, kunde_name, kunde_anrede,
       kunde_adresse, kunde_email, datum, leistungszeitraum, zahlungsziel, status,
       netto, ust_betrag, brutto, notiz, betreff)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16) RETURNING *`,
    [b.firma_id, b.baustelle_id || null, nummer, b.kunde_name || '', b.kunde_anrede || '',
     b.kunde_adresse || '', b.kunde_email || '',
     b.datum || new Date().toISOString().slice(0, 10),
     b.leistungszeitraum || '', Number(b.zahlungsziel) || 30, 'entwurf',
     Number(b.netto) || 0, Number(b.ust_betrag) || 0, Number(b.brutto) || 0,
     b.notiz || '', b.betreff || '']);
  res.json({ ok: true, rechnung: rows[0] });
});

router.patch('/rechnungen/:id', express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const { rows: alt } = await pool.query('SELECT status FROM rechnungen WHERE id=$1', [id]);
  if (!alt.length) return res.status(404).json({ error: 'Nicht gefunden' });
  // Versendete/bezahlte/stornierte Rechnungen nur begrenzt änderbar
  const locked = ['versendet', 'bezahlt', 'storniert'].includes(alt[0].status);
  const b = req.body || {};
  const felder = [], werte = [];
  const add = (k, v) => { felder.push(`${k}=$${felder.length + 1}`); werte.push(v); };
  if (!locked) {
    if (b.kunde_name !== undefined) add('kunde_name', String(b.kunde_name));
    if (b.kunde_anrede !== undefined) add('kunde_anrede', String(b.kunde_anrede));
    if (b.kunde_adresse !== undefined) add('kunde_adresse', String(b.kunde_adresse));
    if (b.kunde_email !== undefined) add('kunde_email', String(b.kunde_email));
    if (b.datum !== undefined) add('datum', String(b.datum));
    if (b.leistungszeitraum !== undefined) add('leistungszeitraum', String(b.leistungszeitraum));
    if (b.zahlungsziel !== undefined) add('zahlungsziel', Number(b.zahlungsziel));
    if (b.netto !== undefined) add('netto', Number(b.netto));
    if (b.ust_betrag !== undefined) add('ust_betrag', Number(b.ust_betrag));
    if (b.brutto !== undefined) add('brutto', Number(b.brutto));
    if (b.betreff !== undefined) add('betreff', String(b.betreff));
    if (b.baustelle_id !== undefined) add('baustelle_id', b.baustelle_id || null);
  }
  if (b.notiz !== undefined) add('notiz', String(b.notiz));
  if (b.status !== undefined) {
    if (b.status === 'bezahlt') add('bezahlt_am', new Date().toISOString());
    add('status', String(b.status));
  }
  add('geaendert_am', new Date().toISOString());
  werte.push(id);
  await pool.query(`UPDATE rechnungen SET ${felder.join(',')} WHERE id=$${werte.length}`, werte);
  res.json({ ok: true });
});

// Storno (nicht löschen – neue Stornorechnung anlegen)
router.post('/rechnungen/:id/storno', async (req, res) => {
  const id = Number(req.params.id);
  const { rows } = await pool.query('SELECT * FROM rechnungen WHERE id=$1', [id]);
  if (!rows.length) return res.status(404).json({ error: 'Nicht gefunden' });
  await pool.query("UPDATE rechnungen SET status='storniert', geaendert_am=now() WHERE id=$1", [id]);
  res.json({ ok: true });
});

// ── Rechnung-Positionen ──────────────────────────────────────────────
router.put('/rechnungen/:id/positionen', express.json(), async (req, res) => {
  const id = Number(req.params.id);
  const { rows: alt } = await pool.query('SELECT status FROM rechnungen WHERE id=$1', [id]);
  if (!alt.length) return res.status(404).json({ error: 'Nicht gefunden' });
  if (['versendet', 'bezahlt', 'storniert'].includes(alt[0].status)) {
    return res.status(409).json({ error: 'Gesperrte Rechnung kann nicht geändert werden' });
  }
  const positionen = req.body.positionen || [];
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query('DELETE FROM rechnung_positionen WHERE rechnung_id=$1', [id]);
    let netto = 0;
    for (let i = 0; i < positionen.length; i++) {
      const p = positionen[i];
      const betrag = Math.round(Number(p.menge) * Number(p.einzelpreis) * 100) / 100;
      netto += betrag;
      await client.query(
        `INSERT INTO rechnung_positionen (rechnung_id, pos, bezeichnung, menge, einheit, einzelpreis, betrag)
         VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [id, i + 1, p.bezeichnung || '', Number(p.menge) || 1, p.einheit || 'Stk.',
         Number(p.einzelpreis) || 0, betrag]);
    }
    // Beträge neu berechnen
    const { rows: f } = await client.query(
      'SELECT besteuerung, ust_satz FROM firmen WHERE id=(SELECT firma_id FROM rechnungen WHERE id=$1)', [id]);
    const firma = f[0] || {};
    const ustSatz = firma.besteuerung === 'kleinunternehmer' ? 0 : (Number(firma.ust_satz) || 19) / 100;
    const ust = Math.round(netto * ustSatz * 100) / 100;
    const brutto = Math.round((netto + ust) * 100) / 100;
    await client.query(
      'UPDATE rechnungen SET netto=$1, ust_betrag=$2, brutto=$3, geaendert_am=now() WHERE id=$4',
      [netto.toFixed(2), ust.toFixed(2), brutto.toFixed(2), id]);
    await client.query('COMMIT');
    res.json({ ok: true, netto, ust, brutto });
  } catch (e) { await client.query('ROLLBACK'); throw e; }
  finally { client.release(); }
});

// ── Rechnung aus Abrechnung erzeugen ────────────────────────────────────
router.post('/rechnungen/aus-abrechnung', express.json(), async (req, res) => {
  const { firma_id, baustelle_id, von, bis, stundensatz } = req.body || {};
  if (!firma_id || !von || !bis) return res.status(400).json({ error: 'firma_id, von, bis nötig' });

  // Stundenzettel im Zeitraum
  const { rows: zettel } = await pool.query(
    `SELECT z.station, z.datum, z.stunden, z.anfahrt, z.techniker,
            COALESCE(SUM(CAST(REPLACE(ze.stunden,',','.') AS NUMERIC)), 0) AS team_stunden
     FROM stundenzettel z
     LEFT JOIN zettel_eintraege ze ON ze.station=z.station AND ze.zeitraum_start=z.zeitraum_start
     WHERE z.zeitraum_start >= $1 AND z.zeitraum_start <= $2
     GROUP BY z.station, z.datum, z.stunden, z.anfahrt, z.techniker`, [von, bis]);

  // Materialverbrauch
  const { rows: mat } = await pool.query(
    `SELECT elem.value AS material, COUNT(*)::int AS anzahl, a.vk_preis
     FROM inspections i
     JOIN rooms r ON r.id=i.room_id
     CROSS JOIN LATERAL jsonb_array_elements_text(i.daten->'arbeiten') AS elem(value)
     LEFT JOIN lager_artikel a ON lower(a.bezeichnung)=lower(elem.value) AND a.aktiv=TRUE
     WHERE COALESCE(i.geloescht,FALSE)=FALSE AND i.datum>=$1 AND i.datum<=$2
     GROUP BY elem.value, a.vk_preis
     HAVING a.vk_preis IS NOT NULL`, [von, bis]);

  // Firma für Stundensatz
  const { rows: firma } = await pool.query('SELECT * FROM firmen WHERE id=$1', [firma_id]);
  const stdSatz = Number(stundensatz) || Number(firma[0]?.stundensatz) || 0;

  // Rechnung anlegen
  const nummer = await naechsteNummer(firma_id);
  const { rows: re } = await pool.query(
    `INSERT INTO rechnungen (firma_id, baustelle_id, nummer, datum, leistungszeitraum,
       status, netto, ust_betrag, brutto, betreff)
     VALUES ($1,$2,$3,$4,$5,'entwurf',0,0,0,$6) RETURNING *`,
    [firma_id, baustelle_id || null, nummer,
     new Date().toISOString().slice(0, 10),
     `${von} bis ${bis}`,
     `Leistungsnachweis TV-Wartung ${von} – ${bis}`]);
  const rechId = re[0].id;

  // Positionen: Stunden
  const positionen = [];
  for (const z of zettel) {
    const std = Number(String(z.stunden || '0').replace(',', '.')) || 0;
    const teamStd = Number(z.team_stunden) || 0;
    const gesamt = std + teamStd;
    if (gesamt > 0 && stdSatz > 0) {
      positionen.push({ bezeichnung: `Arbeitsstunden ${z.station} (${z.datum})`,
        menge: gesamt, einheit: 'Std.', einzelpreis: stdSatz });
    }
  }
  // Positionen: Material
  for (const m of mat) {
    positionen.push({ bezeichnung: m.material, menge: m.anzahl, einheit: 'Stk.', einzelpreis: m.vk_preis });
  }

  // Positionen via eigenem Endpoint speichern
  const fakeReq = { params: { id: rechId }, body: { positionen } };
  let netto = 0;
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    for (let i = 0; i < positionen.length; i++) {
      const p = positionen[i];
      const betrag = Math.round(Number(p.menge) * Number(p.einzelpreis) * 100) / 100;
      netto += betrag;
      await client.query(
        `INSERT INTO rechnung_positionen (rechnung_id, pos, bezeichnung, menge, einheit, einzelpreis, betrag)
         VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [rechId, i + 1, p.bezeichnung, p.menge, p.einheit, p.einzelpreis, betrag]);
    }
    const f = firma[0] || {};
    const ustSatz = f.besteuerung === 'kleinunternehmer' ? 0 : (Number(f.ust_satz) || 19) / 100;
    const ust = Math.round(netto * ustSatz * 100) / 100;
    const brutto = Math.round((netto + ust) * 100) / 100;
    await client.query(
      'UPDATE rechnungen SET netto=$1, ust_betrag=$2, brutto=$3 WHERE id=$4',
      [netto.toFixed(2), ust.toFixed(2), brutto.toFixed(2), rechId]);
    await client.query('COMMIT');
    res.json({ ok: true, rechnung_id: rechId, nummer, netto, ust, brutto });
  } catch (e) { await client.query('ROLLBACK'); throw e; }
  finally { client.release(); }
});

module.exports = router;
