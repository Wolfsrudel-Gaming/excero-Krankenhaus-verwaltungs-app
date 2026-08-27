'use strict';
/**
 * Rechnungs-PDF (pdfkit) – mandantenfähig für alle Geschäftsformen
 * Unterstützt Kleinunternehmer (§19 UStG) und Regelbesteuerung (mit USt)
 */
const PDFDocument = require('pdfkit');
const fs = require('fs');
const path = require('path');
const FILES_DIR = process.env.FILES_DIR || '/data/files';

const TEAL = '#00695C';
const TEAL_LIGHT = '#E0F2EF';
const GRAY = '#757575';
const ROW_ALT = '#F5F8F7';

function fmtEur(n) {
  return Number(n || 0).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
}
function isoToGerman(s) {
  if (!s || !/^\d{4}-\d{2}-\d{2}$/.test(s)) return s || '';
  const [y, m, d] = s.split('-');
  return `${d}.${m}.${y}`;
}
function zahlungszielDatum(datum, tage) {
  const d = new Date(datum);
  d.setDate(d.getDate() + (tage || 30));
  return isoToGerman(d.toISOString().slice(0, 10));
}

async function erzeugePdf(rechnung, positionen) {
  const firma = rechnung; // Firma-Felder sind per JOIN in rechnung enthalten
  const kleinunternehmer = firma.besteuerung === 'kleinunternehmer';

  return new Promise((resolve, reject) => {
    const chunks = [];
    const doc = new PDFDocument({ size: 'A4', margin: 0, info: { Title: `Rechnung ${rechnung.nummer}` } });
    doc.on('data', (c) => chunks.push(c));
    doc.on('end', () => resolve(Buffer.concat(chunks)));
    doc.on('error', reject);

    const MX = 55;
    const MR = 40;
    const W = doc.page.width - MX - MR;
    const PT = 50;

    // ── Firma oben rechts ────────────────────────────────────────────────
    doc.fontSize(8).font('Helvetica').fillColor(GRAY);
    let firmaText = firma.firma_name || '';
    if (firma.adresse) firmaText += `\n${firma.adresse}`;
    if (firma.firma_email) firmaText += `\nE-Mail: ${firma.firma_email}`;
    if (firma.firma_telefon) firmaText += `\nTel.: ${firma.firma_telefon}`;
    if (firma.steuernummer) firmaText += `\nSteuer-Nr.: ${firma.steuernummer}`;
    if (firma.ust_id) firmaText += `\nUSt-IdNr.: ${firma.ust_id}`;
    doc.text(firmaText, MX + W - 190, PT, { width: 190, align: 'right' });

    // Logo
    const logoDateien = [firma.logo_pfad, 'logo.png', 'logo.jpg', 'firmenlogo.png'].filter(Boolean);
    for (const l of logoDateien) {
      const lp = path.isAbsolute(l) ? l : path.join(FILES_DIR, l);
      if (fs.existsSync(lp)) {
        try { doc.image(lp, MX, PT, { height: 50, fit: [160, 50] }); } catch {}
        break;
      }
    }

    // ── Absenderzeile (kleine Schrift, Empfänger-Box) ────────────────────
    const adressY = PT + 75;
    doc.rect(MX, adressY - 14, W / 2, 9).fill('#f8f8f8');
    doc.fontSize(7).fillColor(GRAY).font('Helvetica')
       .text(`${firma.firma_name} · ${(firma.adresse || '').split('\n')[0] || ''}`, MX + 2, adressY - 12);
    doc.fontSize(10).fillColor('#000').font('Helvetica')
       .text(rechnung.kunde_anrede ? `${rechnung.kunde_anrede}\n` : '', MX, adressY, { continued: true });
    doc.text(rechnung.kunde_name || '', MX, adressY);
    if (rechnung.kunde_adresse) {
      doc.fontSize(10).text(rechnung.kunde_adresse, MX, doc.y + 2);
    }

    // ── Betreff + Meta ───────────────────────────────────────────────────
    const metaY = adressY + 85;
    doc.fontSize(16).font('Helvetica-Bold').fillColor(TEAL)
       .text(`Rechnung ${rechnung.nummer}`, MX, metaY);
    doc.moveDown(0.3);
    if (rechnung.betreff) {
      doc.fontSize(10).font('Helvetica').fillColor('#000').text(rechnung.betreff);
      doc.moveDown(0.3);
    }

    // Meta-Tabelle
    const metaFelder = [
      ['Rechnungsdatum', isoToGerman(rechnung.datum)],
      ['Leistungszeitraum', rechnung.leistungszeitraum || ''],
      ['Zahlungsziel', zahlungszielDatum(rechnung.datum, rechnung.zahlungsziel)],
    ].filter(([, v]) => v);
    doc.fontSize(8.5).fillColor(GRAY).font('Helvetica');
    metaFelder.forEach(([l, v]) => {
      doc.text(`${l}: `, MX, doc.y, { continued: true, width: 150 });
      doc.fillColor('#000').text(v, { continued: false });
      doc.fillColor(GRAY);
    });
    doc.moveDown(0.8);

    // ── Positionen-Tabelle ───────────────────────────────────────────────
    const cols = { pos: 25, bez: 240, menge: 45, einh: 45, ep: 65, betrag: 70 };
    const tableX = { pos: MX, bez: MX + cols.pos, menge: MX + cols.pos + cols.bez,
      einh: MX + cols.pos + cols.bez + cols.menge,
      ep: MX + cols.pos + cols.bez + cols.menge + cols.einh,
      betrag: MX + W - cols.betrag };

    // Tabellen-Header
    const thY = doc.y;
    doc.rect(MX, thY, W, 16).fill(TEAL);
    doc.fillColor('white').fontSize(8).font('Helvetica-Bold');
    doc.text('#', tableX.pos, thY + 4, { width: cols.pos });
    doc.text('Bezeichnung', tableX.bez, thY + 4, { width: cols.bez });
    doc.text('Menge', tableX.menge, thY + 4, { width: cols.menge, align: 'right' });
    doc.text('Einheit', tableX.einh, thY + 4, { width: cols.einh, align: 'center' });
    doc.text('Einzelpreis', tableX.ep, thY + 4, { width: cols.ep, align: 'right' });
    doc.text('Betrag', tableX.betrag, thY + 4, { width: cols.betrag, align: 'right' });
    doc.y = thY + 16;

    positionen.forEach((p, i) => {
      if (doc.y + 20 > doc.page.height - 120) {
        doc.addPage({ margin: 0 });
        doc.y = 50;
      }
      const rowY = doc.y;
      const rowH = 15;
      if (i % 2 === 0) doc.rect(MX, rowY, W, rowH).fill(ROW_ALT);
      doc.fillColor('#000').fontSize(9).font('Helvetica');
      doc.text(String(p.pos), tableX.pos, rowY + 3, { width: cols.pos });
      doc.text(p.bezeichnung || '', tableX.bez, rowY + 3, { width: cols.bez - 4 });
      doc.text(Number(p.menge).toLocaleString('de-DE'), tableX.menge, rowY + 3, { width: cols.menge, align: 'right' });
      doc.text(p.einheit || '', tableX.einh, rowY + 3, { width: cols.einh, align: 'center' });
      doc.text(fmtEur(p.einzelpreis), tableX.ep, rowY + 3, { width: cols.ep, align: 'right' });
      doc.font('Helvetica-Bold').text(fmtEur(p.betrag), tableX.betrag, rowY + 3, { width: cols.betrag, align: 'right' });
      doc.y = rowY + rowH;
    });

    // ── Summen ───────────────────────────────────────────────────────────
    doc.moveTo(MX, doc.y).lineTo(MX + W, doc.y).strokeColor('#ccc').lineWidth(0.5).stroke();
    doc.moveDown(0.3);
    const sumX = tableX.ep;
    const sumW = cols.ep + cols.betrag;
    const sumLine = (label, betrag, bold = false) => {
      const y = doc.y;
      doc.fontSize(9).font(bold ? 'Helvetica-Bold' : 'Helvetica').fillColor(bold ? '#000' : GRAY);
      doc.text(label, sumX, y, { width: cols.ep, align: 'right' });
      doc.font(bold ? 'Helvetica-Bold' : 'Helvetica').fillColor('#000')
         .text(fmtEur(betrag), tableX.betrag, y, { width: cols.betrag, align: 'right' });
      doc.y = y + 14;
    };
    sumLine('Nettobetrag:', rechnung.netto);
    if (!kleinunternehmer) {
      sumLine(`USt ${Number(firma.ust_satz || 19).toFixed(0)}%:`, rechnung.ust_betrag);
    }
    doc.rect(sumX, doc.y, sumW, 18).fill(TEAL_LIGHT);
    const bruttoY = doc.y + 3;
    doc.fillColor(TEAL).fontSize(10).font('Helvetica-Bold')
       .text('Gesamtbetrag:', sumX, bruttoY, { width: cols.ep, align: 'right' });
    doc.text(fmtEur(rechnung.brutto), tableX.betrag, bruttoY, { width: cols.betrag, align: 'right' });
    doc.y += 22;

    // ── Kleinunternehmer-Hinweis / Bankverbindung / Fußtext ─────────────
    doc.moveDown(0.5);
    if (kleinunternehmer) {
      doc.fontSize(8).fillColor(GRAY).font('Helvetica')
         .text('Gemäß § 19 UStG wird keine Umsatzsteuer berechnet.', MX, doc.y);
      doc.moveDown(0.4);
    }
    if (firma.iban) {
      doc.fontSize(9).fillColor('#000').font('Helvetica-Bold').text('Bankverbindung:', MX, doc.y);
      doc.font('Helvetica').text(
        `${firma.bank_name || ''} · IBAN: ${firma.iban}${firma.bic ? ` · BIC: ${firma.bic}` : ''}`.trim().replace(/^·\s*/, ''),
        MX, doc.y);
      doc.moveDown(0.4);
    }
    if (rechnung.notiz) {
      doc.fontSize(9).fillColor('#000').text(rechnung.notiz, MX, doc.y);
      doc.moveDown(0.4);
    }

    // Fußtext aus Firmenprofil
    const fuss = firma.rechnungs_fusstext || '';
    if (fuss) {
      doc.fontSize(8).fillColor(GRAY).text(fuss, MX, doc.y, { width: W });
      doc.moveDown(0.3);
    }

    // Seitenzahl
    const pageCount = doc.bufferedPageRange().count;
    for (let i = 0; i < pageCount; i++) {
      doc.switchToPage(i);
      doc.fontSize(7.5).fillColor(GRAY).font('Helvetica')
         .text(
           `${firma.firma_name || ''} · Rechnung ${rechnung.nummer} · Seite ${i + 1} von ${pageCount}`,
           MX, doc.page.height - 28, { width: W });
    }

    doc.end();
  });
}

module.exports = { erzeugePdf };
