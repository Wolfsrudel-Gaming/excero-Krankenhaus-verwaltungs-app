/* global DataGrid, toast, modal, api */
'use strict';

async function viewRechnungen() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = '<div class="loading">Lade Rechnungen…</div>';

  const firmaId = window.aktiveFirmaId;
  const params = new URLSearchParams();
  if (firmaId) params.set('firma_id', firmaId);

  const d = await api(`/excero/api/web/rechnungen?${params}`);
  const rechnungen = d.rechnungen || [];

  const statusLabel = {
    entwurf: '📝 Entwurf', versendet: '📤 Versendet',
    bezahlt: '✅ Bezahlt', storniert: '❌ Storniert', ueberfaellig: '⚠️ Überfällig',
  };
  const statusCls = {
    entwurf: 'badge-draft', versendet: 'badge-info', bezahlt: 'badge-ok',
    storniert: 'badge-err', ueberfaellig: 'badge-warn',
  };

  ca.innerHTML = `
    <div class="view-header">
      <h1>Rechnungen</h1>
      <div class="view-header-actions">
        <button class="btn btn-primary btn-sm" id="neu-re-btn">+ Neue Rechnung</button>
        <button class="btn btn-secondary btn-sm" id="aus-abr-btn">Aus KKH-Abrechnung</button>
      </div>
    </div>
    <div id="re-grid"></div>`;

  const grid = new DataGrid(document.getElementById('re-grid'), {
    columns: [
      { key: 'nummer', label: 'Nummer', width: '120px', sort: true },
      { key: 'datum', label: 'Datum', width: '100px', sort: true },
      { key: 'firma_name', label: 'Firma', width: '140px', sort: true },
      { key: 'kunde_name', label: 'Kunde', width: '200px', sort: true },
      { key: 'leistungszeitraum', label: 'Zeitraum', width: '140px' },
      { key: 'brutto', label: 'Brutto', width: '90px', sort: true, align: 'right',
        render: (v) => `<b>${Number(v).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })}</b>` },
      { key: 'status', label: 'Status', width: '110px', sort: true,
        render: (v) => `<span class="badge ${statusCls[v] || ''}">${statusLabel[v] || v}</span>` },
      { key: '_aktion', label: '', width: '120px',
        render: (_, row) => `
          <a href="/excero/api/web/rechnungen/${row.id}/pdf" target="_blank" class="btn btn-ghost btn-xs">PDF</a>
          <button class="btn btn-ghost btn-xs" onclick="editRechnung(${row.id})">Bearbeiten</button>` },
    ],
    data: rechnungen,
    filterKeys: ['nummer', 'kunde_name', 'status', 'datum'],
  });

  document.getElementById('neu-re-btn').addEventListener('click', () => neueRechnung());
  document.getElementById('aus-abr-btn').addEventListener('click', () => rechnungAusAbrechnung());
}

window.editRechnung = async function(id) {
  const d = await api(`/excero/api/web/rechnungen/${id}`);
  const re = d.rechnung;
  const pos = d.positionen || [];

  const posHtml = pos.map((p, i) => `
    <tr data-idx="${i}">
      <td><input class="form-control form-control-sm p-bez" value="${esc(p.bezeichnung)}"></td>
      <td><input class="form-control form-control-sm p-menge" type="number" step="0.01" value="${p.menge}" style="width:70px"></td>
      <td><input class="form-control form-control-sm p-einh" value="${esc(p.einheit)}" style="width:60px"></td>
      <td><input class="form-control form-control-sm p-ep" type="number" step="0.01" value="${p.einzelpreis}" style="width:80px"></td>
      <td>${Number(p.betrag).toLocaleString('de-DE', { minimumFractionDigits: 2 })} €</td>
      <td><button class="btn btn-ghost btn-xs rem-pos">✕</button></td>
    </tr>`).join('');

  const statusOptionen = ['entwurf','versendet','bezahlt','storniert','ueberfaellig']
    .map((s) => `<option value="${s}" ${re.status === s ? 'selected' : ''}>${s}</option>`).join('');

  const res = await modal(`Rechnung ${re.nummer}`, `
    <div class="form-grid" style="grid-template-columns:1fr 1fr">
      <div class="form-group"><label class="form-label">Kunde</label>
        <input class="form-control" id="re-kunde" value="${esc(re.kunde_name)}"></div>
      <div class="form-group"><label class="form-label">Adresse</label>
        <input class="form-control" id="re-adr" value="${esc(re.kunde_adresse)}"></div>
      <div class="form-group"><label class="form-label">E-Mail</label>
        <input class="form-control" id="re-email" value="${esc(re.kunde_email)}"></div>
      <div class="form-group"><label class="form-label">Datum</label>
        <input class="form-control" type="date" id="re-datum" value="${re.datum}"></div>
      <div class="form-group"><label class="form-label">Zeitraum</label>
        <input class="form-control" id="re-zeitraum" value="${esc(re.leistungszeitraum)}"></div>
      <div class="form-group"><label class="form-label">Zahlungsziel (Tage)</label>
        <input class="form-control" type="number" id="re-ziel" value="${re.zahlungsziel}"></div>
      <div class="form-group"><label class="form-label">Betreff</label>
        <input class="form-control" id="re-betreff" value="${esc(re.betreff || '')}"></div>
      <div class="form-group"><label class="form-label">Status</label>
        <select class="form-control" id="re-status">${statusOptionen}</select></div>
    </div>
    <h4 style="margin:12px 0 6px">Positionen</h4>
    <table class="data-table" style="width:100%">
      <thead><tr><th>Bezeichnung</th><th>Menge</th><th>Einheit</th><th>EP</th><th>Betrag</th><th></th></tr></thead>
      <tbody id="pos-tbody">${posHtml}</tbody>
    </table>
    <button class="btn btn-ghost btn-sm" id="add-pos-btn" style="margin-top:6px">+ Position hinzufügen</button>
    <div class="form-group" style="margin-top:12px"><label class="form-label">Notiz</label>
      <textarea class="form-control" id="re-notiz" rows="2">${esc(re.notiz || '')}</textarea></div>
    <div style="margin-top:8px;display:flex;gap:8px">
      <a href="/excero/api/web/rechnungen/${id}/pdf" target="_blank" class="btn btn-secondary btn-sm">PDF öffnen</a>
      <button class="btn btn-secondary btn-sm" id="versenden-btn">Per E-Mail versenden</button>
      <button class="btn btn-secondary btn-sm" id="storno-btn" style="color:var(--danger)">Stornieren</button>
    </div>`,
    [{ label: 'Schließen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);

  // Position-Buttons
  setTimeout(() => {
    document.getElementById('add-pos-btn')?.addEventListener('click', () => {
      const tr = document.createElement('tr');
      const idx = document.querySelectorAll('#pos-tbody tr').length;
      tr.innerHTML = `<td><input class="form-control form-control-sm p-bez" value=""></td>
        <td><input class="form-control form-control-sm p-menge" type="number" step="0.01" value="1" style="width:70px"></td>
        <td><input class="form-control form-control-sm p-einh" value="Stk." style="width:60px"></td>
        <td><input class="form-control form-control-sm p-ep" type="number" step="0.01" value="0" style="width:80px"></td>
        <td>-</td>
        <td><button class="btn btn-ghost btn-xs rem-pos">✕</button></td>`;
      document.getElementById('pos-tbody').appendChild(tr);
    });
    document.getElementById('pos-tbody')?.addEventListener('click', (e) => {
      if (e.target.classList.contains('rem-pos')) e.target.closest('tr').remove();
    });
    document.getElementById('versenden-btn')?.addEventListener('click', async () => {
      const an = re.kunde_email || '';
      const email = prompt('E-Mail-Adresse des Empfängers:', an);
      if (!email) return;
      try {
        await api(`/excero/api/web/rechnungen/${id}/versenden`, { method: 'POST', body: { an: email } });
        toast('Rechnung versendet');
      } catch (e) { toast(e.message, 'err'); }
    });
    document.getElementById('storno-btn')?.addEventListener('click', async () => {
      if (!confirm(`Rechnung ${re.nummer} stornieren?`)) return;
      await api(`/excero/api/web/rechnungen/${id}/storno`, { method: 'POST' });
      toast('Storniert'); viewRechnungen();
    });
  }, 100);

  if (res !== 'ok') return;

  // Positionen sammeln
  const positionen = [];
  document.querySelectorAll('#pos-tbody tr').forEach((tr) => {
    positionen.push({
      bezeichnung: tr.querySelector('.p-bez')?.value || '',
      menge: Number(tr.querySelector('.p-menge')?.value || 1),
      einheit: tr.querySelector('.p-einh')?.value || 'Stk.',
      einzelpreis: Number(tr.querySelector('.p-ep')?.value || 0),
    });
  });

  try {
    await api(`/excero/api/web/rechnungen/${id}`, { method: 'PATCH', body: {
      kunde_name: document.getElementById('re-kunde')?.value,
      kunde_adresse: document.getElementById('re-adr')?.value,
      kunde_email: document.getElementById('re-email')?.value,
      datum: document.getElementById('re-datum')?.value,
      leistungszeitraum: document.getElementById('re-zeitraum')?.value,
      zahlungsziel: Number(document.getElementById('re-ziel')?.value),
      betreff: document.getElementById('re-betreff')?.value,
      status: document.getElementById('re-status')?.value,
      notiz: document.getElementById('re-notiz')?.value,
    }});
    await api(`/excero/api/web/rechnungen/${id}/positionen`, { method: 'PUT', body: { positionen } });
    toast('Rechnung gespeichert');
    viewRechnungen();
  } catch (e) { toast(e.message, 'err'); }
};

async function neueRechnung() {
  const firmaId = window.aktiveFirmaId;
  if (!firmaId) { toast('Bitte zuerst eine Firma auswählen', 'warn'); return; }
  const nr = await api(`/excero/api/web/rechnungen/next-nr?firma_id=${firmaId}`);

  const res = await modal('Neue Rechnung', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Rechnungsnummer</label>
        <input class="form-control" id="nr-num" value="${esc(nr.nummer)}" readonly></div>
      <div class="form-group"><label class="form-label">Kunde</label>
        <input class="form-control" id="nr-kunde" placeholder="Kundenname"></div>
      <div class="form-group"><label class="form-label">Datum</label>
        <input class="form-control" type="date" id="nr-datum" value="${new Date().toISOString().slice(0, 10)}"></div>
      <div class="form-group"><label class="form-label">Leistungszeitraum</label>
        <input class="form-control" id="nr-zeitraum" placeholder="z.B. 01.06.2026 – 30.06.2026"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    const d = await api('/excero/api/web/rechnungen', { method: 'POST', body: {
      firma_id: firmaId,
      nummer: document.getElementById('nr-num')?.value,
      kunde_name: document.getElementById('nr-kunde')?.value,
      datum: document.getElementById('nr-datum')?.value,
      leistungszeitraum: document.getElementById('nr-zeitraum')?.value,
    }});
    toast('Rechnung angelegt');
    editRechnung(d.rechnung.id);
  } catch (e) { toast(e.message, 'err'); }
}

async function rechnungAusAbrechnung() {
  const firmaId = window.aktiveFirmaId;
  if (!firmaId) { toast('Bitte zuerst eine Firma auswählen', 'warn'); return; }
  const res = await modal('Rechnung aus KKH-Abrechnung', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Von</label>
        <input class="form-control" type="date" id="ra-von"></div>
      <div class="form-group"><label class="form-label">Bis</label>
        <input class="form-control" type="date" id="ra-bis"></div>
      <div class="form-group"><label class="form-label">Stundensatz (€/Std.)</label>
        <input class="form-control" type="number" step="0.01" id="ra-satz" placeholder="Leer = Firmenprofil"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Erstellen', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    const d = await api('/excero/api/web/rechnungen/aus-abrechnung', { method: 'POST', body: {
      firma_id: firmaId,
      von: document.getElementById('ra-von')?.value,
      bis: document.getElementById('ra-bis')?.value,
      stundensatz: Number(document.getElementById('ra-satz')?.value) || undefined,
    }});
    toast(`Rechnung ${d.nummer} angelegt (${Number(d.brutto).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })})`);
    editRechnung(d.rechnung_id);
  } catch (e) { toast(e.message, 'err'); }
}

function esc(s) { return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;'); }
