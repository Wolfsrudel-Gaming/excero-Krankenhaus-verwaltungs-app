/* global DataGrid, toast, modal, api */
'use strict';

async function viewBaustellen() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = '<div class="loading">Lade Baustellen…</div>';

  const firmaId = window.aktiveFirmaId;
  const [dBau, dFirmen, dKunden] = await Promise.all([
    api(`/kkh/api/web/baustellen${firmaId ? `?firma_id=${firmaId}` : ''}`),
    api('/kkh/api/web/firmen'),
    api('/kkh/api/web/kunden'),
  ]);
  const baustellen = dBau.baustellen || [];
  const firmen     = dFirmen.firmen || [];
  const kunden     = dKunden.kunden || [];

  const statusLabel = { aktiv: '🟢 Aktiv', pausiert: '🟡 Pausiert', abgeschlossen: '⬛ Abgeschlossen' };

  ca.innerHTML = `
    <div class="view-header">
      <h1>Baustellen / Projekte</h1>
      <div class="view-header-actions">
        <button class="btn btn-primary btn-sm" id="neu-bau-btn">+ Neue Baustelle</button>
      </div>
    </div>
    <div id="bau-grid"></div>`;

  new DataGrid(document.getElementById('bau-grid'), {
    columns: [
      { key: 'name', label: 'Name', sort: true },
      { key: 'firma_name', label: 'Firma', width: '140px', sort: true },
      { key: 'kunde_name', label: 'Auftraggeber', width: '160px', sort: true },
      { key: 'adresse', label: 'Adresse' },
      { key: 'beginn', label: 'Beginn', width: '100px', sort: true },
      { key: 'ende', label: 'Ende', width: '100px' },
      { key: 'status', label: 'Status', width: '120px', sort: true,
        render: (v) => statusLabel[v] || v },
      { key: '_aktion', label: '', width: '60px',
        render: (_, row) => `<button class="btn btn-ghost btn-xs" onclick="editBaustelle(${row.id})">✏️</button>` },
    ],
    data: baustellen,
    filterKeys: ['name', 'status', 'kunde_name'],
  });

  document.getElementById('neu-bau-btn').addEventListener('click', () => editBaustelle(null, firmen, kunden));
}

window.editBaustelle = async function(id, firmenParam, kundenParam) {
  const firmen = firmenParam || (await api('/kkh/api/web/firmen')).firmen || [];
  const kunden = kundenParam || (await api('/kkh/api/web/kunden')).kunden || [];
  let alt = null;
  if (id) {
    const d = await api(`/kkh/api/web/baustellen`);
    alt = (d.baustellen || []).find((b) => b.id === id);
  }

  const firmaOpts = firmen.map((f) => `<option value="${f.id}" ${alt?.firma_id === f.id ? 'selected' : ''}>${f.name}</option>`).join('');
  const kundeOpts = `<option value="">Kein Auftraggeber</option>` +
    kunden.map((k) => `<option value="${k.id}" ${alt?.kunde_id === k.id ? 'selected' : ''}>${k.name}</option>`).join('');
  const statusOpts = ['aktiv','pausiert','abgeschlossen'].map((s) =>
    `<option value="${s}" ${(alt?.status || 'aktiv') === s ? 'selected' : ''}>${s}</option>`).join('');

  const res = await modal(id ? 'Baustelle bearbeiten' : 'Neue Baustelle', `
    <div class="form-grid">
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Name</label>
        <input class="form-control" id="bau-name" value="${alt?.name || ''}"></div>
      <div class="form-group"><label class="form-label">Firma</label>
        <select class="form-control" id="bau-firma"><option value="">Allgemein</option>${firmaOpts}</select></div>
      <div class="form-group"><label class="form-label">Auftraggeber</label>
        <select class="form-control" id="bau-kunde">${kundeOpts}</select></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Adresse</label>
        <input class="form-control" id="bau-adr" value="${alt?.adresse || ''}"></div>
      <div class="form-group"><label class="form-label">Status</label>
        <select class="form-control" id="bau-status">${statusOpts}</select></div>
      <div class="form-group"><label class="form-label">Stundensatz (€/Std.)</label>
        <input class="form-control" type="number" step="0.01" id="bau-satz" value="${alt?.stundensatz || ''}"></div>
      <div class="form-group"><label class="form-label">Beginn</label>
        <input class="form-control" type="date" id="bau-beginn" value="${alt?.beginn || ''}"></div>
      <div class="form-group"><label class="form-label">Ende</label>
        <input class="form-control" type="date" id="bau-ende" value="${alt?.ende || ''}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="bau-notiz" rows="2">${alt?.notiz || ''}</textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;

  const body = {
    name: document.getElementById('bau-name')?.value,
    firma_id: document.getElementById('bau-firma')?.value || null,
    kunde_id: document.getElementById('bau-kunde')?.value || null,
    adresse: document.getElementById('bau-adr')?.value,
    status: document.getElementById('bau-status')?.value,
    stundensatz: Number(document.getElementById('bau-satz')?.value) || null,
    beginn: document.getElementById('bau-beginn')?.value,
    ende: document.getElementById('bau-ende')?.value,
    notiz: document.getElementById('bau-notiz')?.value,
  };
  try {
    if (id) await api(`/kkh/api/web/baustellen/${id}`, { method: 'PATCH', body });
    else await api('/kkh/api/web/baustellen', { method: 'POST', body });
    toast('Gespeichert');
    viewBaustellen();
  } catch (e) { toast(e.message, 'err'); }
};
