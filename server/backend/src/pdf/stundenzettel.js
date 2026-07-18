'use strict';
/**
 * Server-seitige Stundenzettel-PDF-Erzeugung (pdfkit)
 * Repliziert das App-Layout aus StundenzettelPdf.kt
 */
const PDFDocument = require('pdfkit');
const fs = require('fs');
const path = require('path');
const FILES_DIR = process.env.FILES_DIR || '/data/files';

const TEAL = '#00695C';
const TEAL_LIGHT = '#E0F2EF';
const GRAY = '#757575';
const ROW_ALT = '#F5F8F7';

/** Erzeugt den Stundenzettel-PDF als Buffer */
async function erzeugePdf(daten) {
  const {
    station, zeitraum, zeitraumStart, auftragsnummer,
    datum, techniker,
    eintraege = [],      // [{mitarbeiter, stunden, anfahrt}]
    leistungen = [],     // [{zimmer, datum, arbeiten: []}]
    material = [],       // [{bezeichnung, anzahl}]
    signaturStation,     // Pfad zur PNG-Datei oder null
    signaturTechniker,
  } = daten;

  return new Promise((resolve, reject) => {
    const chunks = [];
    const doc = new PDFDocument({ size: 'A4', margin: 40, info: { Title: `Stundenzettel ${station}` } });
    doc.on('data', (c) => chunks.push(c));
    doc.on('end', () => resolve(Buffer.concat(chunks)));
    doc.on('error', reject);

    const W = doc.page.width - 80; // content width (margins 40 each side)
    const MX = 40;

    // ── Header ──────────────────────────────────────────────────────────
    doc.rect(0, 0, doc.page.width, 72).fill(TEAL);
    doc.fillColor('white').fontSize(17).font('Helvetica-Bold')
       .text('Stundenzettel / Leistungsnachweis', MX, 18);
    doc.fillColor('white').fontSize(9).font('Helvetica')
       .text('TV-Wartung Freenet-Empfangsgeräte', MX, 40);
    doc.y = 90;

    // Logo (falls vorhanden)
    const logoDateien = ['logo.png', 'logo.jpg', 'firmenlogo.png'];
    for (const l of logoDateien) {
      const lp = path.join(FILES_DIR, l);
      if (fs.existsSync(lp)) {
        try {
          doc.image(lp, doc.page.width - 140, 8, { height: 56, fit: [120, 56] });
        } catch {}
        break;
      }
    }

    // Auftraggeber/Station-Box
    doc.roundedRect(MX, doc.y, W, 62, 6).fill(TEAL_LIGHT);
    const boxY = doc.y;
    doc.fillColor(GRAY).fontSize(8).font('Helvetica').text('Auftraggeber', MX + 10, boxY + 12);
    doc.fillColor('#000').fontSize(10).font('Helvetica-Bold').text('Kinderklinik Köln', MX + 10, boxY + 24);
    doc.fillColor('#000').fontSize(9).font('Helvetica').text('Amsterdamer Straße 59, 50735 Köln', MX + 10, boxY + 37);
    const col2 = MX + W / 2;
    const auftragsText = `Station${auftragsnummer ? ` · Auftrag ${auftragsnummer}` : ''}`;
    doc.fillColor(GRAY).fontSize(8).font('Helvetica').text(auftragsText, col2 + 10, boxY + 12);
    doc.fillColor('#000').fontSize(10).font('Helvetica-Bold').text(station, col2 + 10, boxY + 24);
    doc.fillColor('#000').fontSize(9).font('Helvetica').text(`Zeitraum: ${zeitraum}`, col2 + 10, boxY + 37);
    doc.y = boxY + 62 + 16;

    // ── Team-Zeiten ──────────────────────────────────────────────────────
    if (eintraege.length > 0) {
      doc.fillColor(TEAL).fontSize(12).font('Helvetica-Bold').text('Arbeitszeiten', MX, doc.y);
      doc.moveDown(0.4);
      doc.fillColor(GRAY).fontSize(8.5).font('Helvetica-Bold');
      doc.text('Mitarbeiter', MX + 4, doc.y, { continued: true, width: 220 });
      doc.text('Arbeitsstunden', MX + 260, doc.y, { continued: true, width: 100 });
      doc.text('Anfahrt', MX + 370, doc.y, { width: 80 });
      doc.moveDown(0.2);
      doc.moveTo(MX, doc.y).lineTo(MX + W, doc.y).strokeColor('#b4b4b4').stroke();
      const parse = (s) => Number(String(s || '0').replace(',', '.')) || 0;
      let sumStd = 0, sumAnf = 0;
      eintraege.forEach((e, i) => {
        const rowY = doc.y;
        if (i % 2 === 0) doc.rect(MX, rowY, W, 16).fill(ROW_ALT);
        doc.fillColor('#000').fontSize(9.5).font('Helvetica').text(e.mitarbeiter, MX + 4, rowY + 4, { width: 220 });
        const std = parse(e.stunden);
        const anf = parse(e.anfahrt);
        sumStd += std;
        sumAnf += anf;
        doc.text(std ? `${e.stunden} Std.` : '–', MX + 260, rowY + 4, { width: 100 });
        doc.text(anf ? `${e.anfahrt} Std.` : '–', MX + 370, rowY + 4, { width: 80 });
        doc.y = rowY + 16;
      });
      doc.moveTo(MX, doc.y).lineTo(MX + W, doc.y).strokeColor('#b4b4b4').stroke();
      const sumY = doc.y;
      const fmtNum = (n) => Number(n.toFixed(2)).toString().replace('.', ',');
      doc.fillColor('#000').fontSize(9.5).font('Helvetica-Bold').text('Gesamt', MX + 4, sumY + 4, { width: 220 });
      doc.text(`${fmtNum(sumStd)} Std.`, MX + 260, sumY + 4, { width: 100 });
      doc.text(`${fmtNum(sumAnf)} Std.`, MX + 370, sumY + 4, { width: 80 });
      doc.y = sumY + 22;
      if (datum) {
        doc.fillColor(GRAY).fontSize(9).font('Helvetica').text(`Datum der Leistung: ${datum}`, MX + 4, doc.y);
        doc.moveDown(0.5);
      }
    } else {
      // Einzeltechniker
      doc.roundedRect(MX, doc.y, W, 40, 6).fill(ROW_ALT);
      const zy = doc.y;
      const felder = [['Datum', datum], ['Techniker', techniker]];
      const cw = W / felder.length;
      felder.forEach(([l, v], i) => {
        const x = MX + i * cw + 10;
        doc.fillColor(GRAY).fontSize(8).font('Helvetica').text(l, x, zy + 8);
        doc.fillColor('#000').fontSize(10).font('Helvetica-Bold').text(v || '–', x, zy + 20);
      });
      doc.y = zy + 40 + 14;
    }

    // ── Leistungen ───────────────────────────────────────────────────────
    doc.fillColor(TEAL).fontSize(12).font('Helvetica-Bold').text('Durchgeführte Leistungen', MX, doc.y);
    doc.moveDown(0.4);
    doc.fillColor(GRAY).fontSize(8.5).font('Helvetica-Bold');
    doc.text('Zimmer', MX + 4, doc.y, { continued: true, width: 60 });
    doc.text('Datum', MX + 68, doc.y, { continued: true, width: 74 });
    doc.text('Arbeiten / verbautes Material', MX + 148, doc.y, { width: W - 148 });
    doc.moveDown(0.2);
    doc.moveTo(MX, doc.y).lineTo(MX + W, doc.y).strokeColor('#b4b4b4').stroke();

    if (leistungen.length === 0) {
      doc.fillColor(GRAY).fontSize(9).font('Helvetica')
         .text('Keine Prüfungen im gewählten Zeitraum erfasst.', MX + 4, doc.y + 6);
      doc.moveDown(1);
    } else {
      leistungen.forEach((l, i) => {
        const text = l.arbeiten.length ? 'TV überprüft; ' + l.arbeiten.join(', ') : 'TV überprüft';
        const rowH = Math.max(16, 8 + Math.ceil(doc.widthOfString(text) / (W - 152)) * 11);
        if (doc.y + rowH > doc.page.height - 100) doc.addPage();
        const rowY = doc.y;
        if (i % 2 === 0) doc.rect(MX, rowY, W, rowH).fill(ROW_ALT);
        doc.fillColor('#000').fontSize(9).font('Helvetica-Bold').text(l.zimmer, MX + 4, rowY + 4, { width: 58 });
        doc.fillColor('#000').fontSize(9).font('Helvetica').text(
          isoToGerman(l.datum), MX + 68, rowY + 4, { width: 72 });
        doc.text(text, MX + 148, rowY + 4, { width: W - 152 });
        doc.y = rowY + rowH;
      });
    }
    doc.moveDown(1);

    // ── Material ─────────────────────────────────────────────────────────
    if (material.length > 0) {
      if (doc.y + 30 + material.length * 13 > doc.page.height - 100) doc.addPage();
      doc.fillColor(TEAL).fontSize(12).font('Helvetica-Bold').text('Materialnachweis', MX, doc.y);
      doc.moveDown(0.4);
      material.forEach((m) => {
        doc.fillColor('#000').fontSize(9.5).font('Helvetica')
           .text(`•  ${m.bezeichnung}`, MX + 6, doc.y, { continued: true, width: W - 60 });
        doc.font('Helvetica-Bold').text(`${m.anzahl}×`, { width: 50, align: 'right' });
        doc.y += 1;
      });
      doc.moveDown(1);
    }

    // ── Unterschriften ───────────────────────────────────────────────────
    if (doc.y + 120 > doc.page.height - 40) doc.addPage();
    doc.fillColor(TEAL).fontSize(12).font('Helvetica-Bold').text('Bestätigung', MX, doc.y);
    doc.moveDown(0.8);
    const sigTop = doc.y;
    const sigH = 50;
    const colW = (W - 30) / 2;
    const x2 = MX + colW + 30;
    const lineY = sigTop + sigH;

    // Signaturen einbetten
    for (const [sigPfad, x] of [[signaturStation, MX], [signaturTechniker, x2]]) {
      if (sigPfad && fs.existsSync(sigPfad)) {
        try {
          doc.image(sigPfad, x, sigTop, { fit: [colW, sigH - 4] });
        } catch {}
      }
    }

    doc.moveTo(MX, lineY).lineTo(MX + colW, lineY).strokeColor('#000').lineWidth(0.8).stroke();
    doc.fillColor(GRAY).fontSize(8.5).font('Helvetica')
       .text('Unterschrift Station (Datum, Name, Stempel)', MX, lineY + 4);
    doc.moveTo(x2, lineY).lineTo(x2 + colW, lineY).strokeColor('#000').stroke();
    doc.text(techniker ? `Unterschrift Dienstleister: ${techniker}` : 'Unterschrift Dienstleister', x2, lineY + 4);

    // Seitenzahlen + Fußzeile
    const pageCount = doc.bufferedPageRange().count;
    for (let i = 0; i < pageCount; i++) {
      doc.switchToPage(i);
      doc.fillColor(GRAY).fontSize(7.5).font('Helvetica')
         .text(
           `KKH TV-Wartung · Stundenzettel · ${new Date().toLocaleDateString('de-DE')} · Seite ${i + 1} von ${pageCount}`,
           MX, doc.page.height - 28, { width: W });
    }

    doc.end();
  });
}

function isoToGerman(s) {
  if (!s || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return s || '';
  const [y, m, d] = s.split('-');
  return `${d}.${m}.${y}`;
}

module.exports = { erzeugePdf };
