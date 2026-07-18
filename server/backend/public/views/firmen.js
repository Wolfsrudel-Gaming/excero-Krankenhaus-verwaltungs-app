/* global DataGrid, toast, modal, api */
'use strict';

async function viewFirmen() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = '<div class="loading">Lade Firmen…</div>';

  const d = await api('/kkh/api/web/firmen');
  const firmen = d.firmen || [];

  ca.innerHTML = `
    <div class="view-header">
      <h1>Firmen / Mandanten</h1>
      <div class="view-header-actions">
        <button class="btn btn-primary btn-sm" id="neu-firma-btn">+ Neue Firma</button>
      </div>
    </div>
    <div id="firma-grid"></div>`;

  new DataGrid(document.getElementById('firma-grid'), {
    columns: [
      { key: 'name', label: 'Firmenname', sort: true },
      { key: 'rechtsform', label: 'Rechtsform', width: '120px' },
      { key: 'besteuerung', label: 'Besteuerung', width: '130px',
        render: (v) => v === 'kleinunternehmer' ? '§19 KU' : `Regelbesteuerung` },
      { key: 'rechnungs_prefix', label: 'Prefix', width: '80px' },
      { key: 'stundensatz', label: 'Std.-Satz', width: '90px', align: 'right',
        render: (v) => v ? `${Number(v).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' })}/Std.` : '–' },
      { key: 'email', label: 'E-Mail', width: '180px' },
      { key: 'aktiv', label: 'Aktiv', width: '70px',
        render: (v) => v ? '✅' : '❌' },
      { key: '_aktion', label: '', width: '60px',
        render: (_, row) => `<button class="btn btn-ghost btn-xs" onclick="editFirma(${row.id})">✏️</button>` },
    ],
    data: firmen,
    filterKeys: ['name', 'rechtsform'],
  });

  document.getElementById('neu-firma-btn').addEventListener('click', () => editFirma(null));
}

window.editFirma = async function(id) {
  let alt = null;
  if (id) {
    const d = await api('/kkh/api/web/firmen');
    alt = (d.firmen || []).find((f) => f.id === id);
  }

  const bestOpts = ['regel','kleinunternehmer'].map((v) =>
    `<option value="${v}" ${(alt?.besteuerung || 'regel') === v ? 'selected':''}>${v === 'regel' ? 'Regelbesteuerung (19% USt)' : 'Kleinunternehmer (§19 UStG)'}</option>`).join('');

  const res = await modal(id ? `Firma: ${alt?.name || ''}` : 'Neue Firma', `
    <div class="form-grid" style="grid-template-columns:1fr 1fr">
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Firmenname</label>
        <input class="form-control" id="f-name" value="${alt?.name || ''}"></div>
      <div class="form-group"><label class="form-label">Rechtsform</label>
        <input class="form-control" id="f-rf" value="${alt?.rechtsform || ''}" placeholder="GmbH, UG, Einzelunternehmen…"></div>
      <div class="form-group"><label class="form-label">Rechnungs-Prefix</label>
        <input class="form-control" id="f-pre" value="${alt?.rechnungs_prefix || 'RE'}" placeholder="RE, EX, WM…"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Adresse</label>
        <textarea class="form-control" id="f-adr" rows="2">${alt?.adresse || ''}</textarea></div>
      <div class="form-group"><label class="form-label">Besteuerung</label>
        <select class="form-control" id="f-best">${bestOpts}</select></div>
      <div class="form-group"><label class="form-label">USt-Satz (%)</label>
        <input class="form-control" type="number" id="f-ust" value="${alt?.ust_satz || 19}" step="0.01"></div>
      <div class="form-group"><label class="form-label">Steuernummer</label>
        <input class="form-control" id="f-steuernr" value="${alt?.steuernummer || ''}"></div>
      <div class="form-group"><label class="form-label">USt-IdNr.</label>
        <input class="form-control" id="f-ustid" value="${alt?.ust_id || ''}"></div>
      <div class="form-group"><label class="form-label">Standard-Stundensatz (€)</label>
        <input class="form-control" type="number" step="0.01" id="f-satz" value="${alt?.stundensatz || ''}"></div>
      <div class="form-group"><label class="form-label">E-Mail</label>
        <input class="form-control" id="f-email" value="${alt?.email || ''}"></div>
      <div class="form-group"><label class="form-label">Telefon</label>
        <input class="form-control" id="f-tel" value="${alt?.telefon || ''}"></div>
      <div class="form-group"><label class="form-label">Webseite</label>
        <input class="form-control" id="f-web" value="${alt?.webseite || ''}"></div>
      <div class="form-group"><label class="form-label">IBAN</label>
        <input class="form-control" id="f-iban" value="${alt?.iban || ''}"></div>
      <div class="form-group"><label class="form-label">BIC</label>
        <input class="form-control" id="f-bic" value="${alt?.bic || ''}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Bankname</label>
        <input class="form-control" id="f-bank" value="${alt?.bank_name || ''}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Rechnungs-Fußtext</label>
        <textarea class="form-control" id="f-fuss" rows="2">${alt?.rechnungs_fusstext || ''}</textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;

  const body = {
    name: document.getElementById('f-name')?.value,
    rechtsform: document.getElementById('f-rf')?.value,
    rechnungs_prefix: document.getElementById('f-pre')?.value,
    adresse: document.getElementById('f-adr')?.value,
    besteuerung: document.getElementById('f-best')?.value,
    ust_satz: Number(document.getElementById('f-ust')?.value),
    steuernummer: document.getElementById('f-steuernr')?.value,
    ust_id: document.getElementById('f-ustid')?.value,
    stundensatz: Number(document.getElementById('f-satz')?.value) || null,
    email: document.getElementById('f-email')?.value,
    telefon: document.getElementById('f-tel')?.value,
    webseite: document.getElementById('f-web')?.value,
    iban: document.getElementById('f-iban')?.value,
    bic: document.getElementById('f-bic')?.value,
    bank_name: document.getElementById('f-bank')?.value,
    rechnungs_fusstext: document.getElementById('f-fuss')?.value,
  };
  try {
    if (id) await api(`/kkh/api/web/firmen/${id}`, { method: 'PATCH', body });
    else await api('/kkh/api/web/firmen', { method: 'POST', body });
    toast('Firma gespeichert');
    viewFirmen();
    // Mandanten-Dropdown neu laden
    ladeFirmen();
  } catch (e) { toast(e.message, 'err'); }
};
