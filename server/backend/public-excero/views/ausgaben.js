/* global DataGrid, toast, modal, api */
'use strict';

async function viewAusgaben() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = '<div class="loading">Lade Ausgaben…</div>';

  const firmaId = window.aktiveFirmaId;
  const params = new URLSearchParams();
  if (firmaId) params.set('firma_id', firmaId);

  const [dAusg, dBau] = await Promise.all([
    api(`/excero/api/web/ausgaben?${params}`),
    api('/excero/api/web/baustellen'),
  ]);
  const ausgaben  = dAusg.ausgaben || [];
  const baustellen = dBau.baustellen || [];

  ca.innerHTML = `
    <div class="view-header">
      <h1>Ausgaben / Kosten</h1>
      <div class="view-header-actions">
        <button class="btn btn-primary btn-sm" id="neu-ausg-btn">+ Neue Ausgabe</button>
      </div>
    </div>
    <div id="ausg-grid"></div>`;

  const grid = new DataGrid(document.getElementById('ausg-grid'), {
    columns: [
      { key: 'datum', label: 'Datum', width: '100px', sort: true },
      { key: 'kategorie', label: 'Kategorie', width: '130px', sort: true },
      { key: 'bezeichnung', label: 'Bezeichnung', sort: true },
      { key: 'baustelle_name', label: 'Baustelle', width: '140px', sort: true },
      { key: 'betrag', label: 'Betrag', width: '100px', sort: true, align: 'right',
        render: (v) => `<b style="color:var(--danger)">${Number(v).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })}</b>` },
      { key: 'beleg_notiz', label: 'Beleg', width: '120px' },
      { key: '_aktion', label: '', width: '60px',
        render: (_, row) => `<button class="btn btn-ghost btn-xs" onclick="editAusgabe(${row.id})">✏️</button>
          <button class="btn btn-ghost btn-xs" onclick="delAusgabe(${row.id})">✕</button>` },
    ],
    data: ausgaben,
    filterKeys: ['datum', 'kategorie', 'bezeichnung'],
  });

  document.getElementById('neu-ausg-btn').addEventListener('click', () => editAusgabe(null, baustellen));
}

window.editAusgabe = async function(id, baustellenParam) {
  const baustellen = baustellenParam || (await api('/excero/api/web/baustellen')).baustellen || [];
  const alt = id ? (await api(`/excero/api/web/ausgaben?` + new URLSearchParams({ firma_id: window.aktiveFirmaId || '' }))).ausgaben.find((a) => a.id === id) : null;

  const bauOpts = `<option value="">Keine Baustelle</option>` +
    baustellen.map((b) => `<option value="${b.id}" ${alt?.baustelle_id === b.id ? 'selected' : ''}>${b.name}</option>`).join('');

  const res = await modal(id ? 'Ausgabe bearbeiten' : 'Neue Ausgabe', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Datum</label>
        <input class="form-control" type="date" id="au-datum" value="${alt?.datum || new Date().toISOString().slice(0,10)}"></div>
      <div class="form-group"><label class="form-label">Kategorie</label>
        <input class="form-control" id="au-kat" value="${alt?.kategorie || ''}" placeholder="Material, Fahrt, Werkzeug…"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Bezeichnung</label>
        <input class="form-control" id="au-bez" value="${alt?.bezeichnung || ''}"></div>
      <div class="form-group"><label class="form-label">Betrag (€)</label>
        <input class="form-control" type="number" step="0.01" id="au-bet" value="${alt?.betrag || ''}"></div>
      <div class="form-group"><label class="form-label">Baustelle</label>
        <select class="form-control" id="au-bau">${bauOpts}</select></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Belegnummer / Notiz</label>
        <input class="form-control" id="au-notiz" value="${alt?.beleg_notiz || ''}"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;

  const body = {
    firma_id: window.aktiveFirmaId,
    datum: document.getElementById('au-datum')?.value,
    kategorie: document.getElementById('au-kat')?.value,
    bezeichnung: document.getElementById('au-bez')?.value,
    betrag: Number(document.getElementById('au-bet')?.value),
    baustelle_id: document.getElementById('au-bau')?.value || null,
    beleg_notiz: document.getElementById('au-notiz')?.value,
  };
  try {
    if (id) await api(`/excero/api/web/ausgaben/${id}`, { method: 'PATCH', body });
    else await api('/excero/api/web/ausgaben', { method: 'POST', body });
    toast('Gespeichert');
    viewAusgaben();
  } catch (e) { toast(e.message, 'err'); }
};

window.delAusgabe = async function(id) {
  if (!confirm('Ausgabe löschen?')) return;
  await api(`/excero/api/web/ausgaben/${id}`, { method: 'DELETE' });
  toast('Gelöscht');
  viewAusgaben();
};
