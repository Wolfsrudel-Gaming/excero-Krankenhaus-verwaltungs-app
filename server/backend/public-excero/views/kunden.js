/* global DataGrid, toast, modal, api */
'use strict';

async function viewKunden() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = '<div class="loading">Lade Kunden…</div>';

  const d = await api('/excero/api/web/kunden');
  const kunden = d.kunden || [];

  ca.innerHTML = `
    <div class="view-header">
      <h1>Kunden</h1>
      <div class="view-header-actions">
        <button class="btn btn-primary btn-sm" id="neu-k-btn">+ Neuer Kunde</button>
      </div>
    </div>
    <div id="k-grid"></div>`;

  new DataGrid(document.getElementById('k-grid'), {
    columns: [
      { key: 'name', label: 'Name', sort: true },
      { key: 'anrede', label: 'Anrede', width: '100px' },
      { key: 'email', label: 'E-Mail', width: '200px', sort: true },
      { key: 'telefon', label: 'Telefon', width: '140px' },
      { key: 'adresse', label: 'Adresse' },
      { key: '_aktion', label: '', width: '60px',
        render: (_, row) => `<button class="btn btn-ghost btn-xs" onclick="editKunde(${row.id})">✏️</button>` },
    ],
    data: kunden,
    filterKeys: ['name', 'email'],
  });

  document.getElementById('neu-k-btn').addEventListener('click', () => editKunde(null));
}

window.editKunde = async function(id) {
  let alt = null;
  if (id) {
    const d = await api('/excero/api/web/kunden');
    alt = (d.kunden || []).find((k) => k.id === id);
  }

  const res = await modal(id ? 'Kunde bearbeiten' : 'Neuer Kunde', `
    <div class="form-grid" style="grid-template-columns:1fr 1fr">
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Name / Firma</label>
        <input class="form-control" id="k-name" value="${alt?.name || ''}"></div>
      <div class="form-group"><label class="form-label">Anrede</label>
        <input class="form-control" id="k-anrede" value="${alt?.anrede || ''}" placeholder="Sehr geehrte Damen und Herren,"></div>
      <div class="form-group"><label class="form-label">E-Mail</label>
        <input class="form-control" type="email" id="k-email" value="${alt?.email || ''}"></div>
      <div class="form-group"><label class="form-label">Telefon</label>
        <input class="form-control" id="k-tel" value="${alt?.telefon || ''}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Adresse</label>
        <textarea class="form-control" id="k-adr" rows="3">${alt?.adresse || ''}</textarea></div>
      <div class="form-group"><label class="form-label">Steuernummer</label>
        <input class="form-control" id="k-steuernr" value="${alt?.steuernummer || ''}"></div>
      <div class="form-group"><label class="form-label">USt-IdNr.</label>
        <input class="form-control" id="k-ustid" value="${alt?.ust_id || ''}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="k-notiz" rows="2">${alt?.notiz || ''}</textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;

  const body = {
    name: document.getElementById('k-name')?.value,
    anrede: document.getElementById('k-anrede')?.value,
    email: document.getElementById('k-email')?.value,
    telefon: document.getElementById('k-tel')?.value,
    adresse: document.getElementById('k-adr')?.value,
    steuernummer: document.getElementById('k-steuernr')?.value,
    ust_id: document.getElementById('k-ustid')?.value,
    notiz: document.getElementById('k-notiz')?.value,
  };
  try {
    if (id) await api(`/excero/api/web/kunden/${id}`, { method: 'PATCH', body });
    else await api('/excero/api/web/kunden', { method: 'POST', body });
    toast('Gespeichert');
    viewKunden();
  } catch (e) { toast(e.message, 'err'); }
};
