'use strict';
/**
 * HiDrive WebDAV-Browser (Strato HiDrive / generischer WebDAV)
 * Zugangsdaten aus einstellungen.hidrive
 */
const express = require('express');
const router = express.Router();
const { pool } = require('../db');
const path = require('path');
const fs = require('fs');

async function getHiDriveCreds() {
  const { rows } = await pool.query("SELECT wert FROM einstellungen WHERE key='hidrive'");
  return rows[0]?.wert || null;
}

function fehltConfig(res) {
  return res.status(503).json({
    error: 'HiDrive nicht konfiguriert',
    hinweis: 'Bitte HiDrive-Zugangsdaten unter Einstellungen hinterlegen.'
  });
}

async function webdavRequest(creds, method, pfad, body, extraHeaders = {}) {
  const url = `${creds.url.replace(/\/$/, '')}${pfad}`;
  const auth = Buffer.from(`${creds.user}:${creds.passwort}`).toString('base64');
  const opts = {
    method,
    headers: {
      Authorization: `Basic ${auth}`,
      ...extraHeaders,
    },
  };
  if (body) { opts.body = body; }
  return fetch(url, opts);
}

// Ordnerinhalt auflisten (PROPFIND, Tiefe 1)
router.get('/hidrive/list', async (req, res) => {
  const creds = await getHiDriveCreds();
  if (!creds?.url) return fehltConfig(res);
  const pfad = String(req.query.pfad || '/');
  try {
    const r = await webdavRequest(creds, 'PROPFIND', pfad, null, { Depth: '1', 'Content-Type': 'application/xml' });
    if (!r.ok) return res.status(r.status).json({ error: `WebDAV ${r.status}` });
    const xml = await r.text();
    // Einfaches XML-Parsing für href, displayname, getcontentlength, resourcetype
    const eintraege = [];
    const responseRE = /<[^:]*:?response[^>]*>([\s\S]*?)<\/[^:]*:?response>/gi;
    let m;
    while ((m = responseRE.exec(xml))) {
      const block = m[1];
      const href = (/<[^:]*:?href[^>]*>([\s\S]*?)<\/[^:]*:?href>/i.exec(block)?.[1] || '').trim();
      const isCol = /<[^:]*:?collection/i.test(block);
      const size = Number(/<[^:]*:?getcontentlength[^>]*>(\d+)<\/[^:]*:?getcontentlength>/i.exec(block)?.[1] || 0);
      const modified = (/<[^:]*:?getlastmodified[^>]*>([^<]+)<\/[^:]*:?getlastmodified>/i.exec(block)?.[1] || '').trim();
      const name = decodeURIComponent(href.split('/').filter(Boolean).pop() || '');
      if (name) eintraege.push({ href, name, isOrdner: isCol, groesse: size, geaendert: modified });
    }
    res.json({ pfad, eintraege: eintraege.filter((e, i) => i > 0 || e.href !== pfad) });
  } catch (e) { res.status(502).json({ error: e.message }); }
});

// Datei herunterladen (streamen)
router.get('/hidrive/download', async (req, res) => {
  const creds = await getHiDriveCreds();
  if (!creds?.url) return fehltConfig(res);
  const pfad = String(req.query.pfad || '');
  if (!pfad) return res.status(400).json({ error: 'pfad fehlt' });
  try {
    const r = await webdavRequest(creds, 'GET', pfad);
    if (!r.ok) return res.status(r.status).json({ error: `WebDAV ${r.status}` });
    const ct = r.headers.get('content-type') || 'application/octet-stream';
    const name = decodeURIComponent(pfad.split('/').pop() || 'datei');
    res.setHeader('Content-Type', ct);
    res.setHeader('Content-Disposition', `attachment; filename="${name}"`);
    const buf = await r.arrayBuffer();
    res.send(Buffer.from(buf));
  } catch (e) { res.status(502).json({ error: e.message }); }
});

// Datei hochladen (PUT)
router.put('/hidrive/upload', express.raw({ type: () => true, limit: '500mb' }), async (req, res) => {
  const creds = await getHiDriveCreds();
  if (!creds?.url) return fehltConfig(res);
  const pfad = String(req.query.pfad || '');
  if (!pfad) return res.status(400).json({ error: 'pfad fehlt' });
  try {
    const r = await webdavRequest(creds, 'PUT', pfad, req.body, {
      'Content-Type': req.headers['content-type'] || 'application/octet-stream',
    });
    res.json({ ok: r.ok, status: r.status });
  } catch (e) { res.status(502).json({ error: e.message }); }
});

// Ordner erstellen (MKCOL)
router.post('/hidrive/mkdir', express.json(), async (req, res) => {
  const creds = await getHiDriveCreds();
  if (!creds?.url) return fehltConfig(res);
  const pfad = String(req.body?.pfad || '');
  if (!pfad) return res.status(400).json({ error: 'pfad fehlt' });
  try {
    const r = await webdavRequest(creds, 'MKCOL', pfad);
    res.json({ ok: r.ok || r.status === 405, status: r.status });
  } catch (e) { res.status(502).json({ error: e.message }); }
});

// Löschen
router.delete('/hidrive/delete', async (req, res) => {
  const creds = await getHiDriveCreds();
  if (!creds?.url) return fehltConfig(res);
  const pfad = String(req.query.pfad || '');
  if (!pfad) return res.status(400).json({ error: 'pfad fehlt' });
  try {
    const r = await webdavRequest(creds, 'DELETE', pfad);
    res.json({ ok: r.ok, status: r.status });
  } catch (e) { res.status(502).json({ error: e.message }); }
});

module.exports = router;
