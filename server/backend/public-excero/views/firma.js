/* global toast, api */
'use strict';

async function viewFirma() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = '<div class="loading">Lade Firmenprofil…</div>';

  const d = await api('/excero/api/web/firmen');
  const f = (d.firmen || [])[0];
  if (!f) { ca.innerHTML = '<p class="empty-hint">Keine Firma hinterlegt.</p>'; return; }

  const bestOpts = ['regel','kleinunternehmer'].map((v) =>
    `<option value="${v}" ${f.besteuerung === v ? 'selected':''}>${v === 'regel' ? 'Regelbesteuerung (19% USt)' : 'Kleinunternehmer (§19 UStG)'}</option>`).join('');

  ca.innerHTML = `
    <div class="view-header"><h1>Firmenprofil</h1></div>
    <div class="settings-section">
      <h3>🏢 ${f.name}</h3>
      <div class="form-grid" style="grid-template-columns:1fr 1fr;max-width:800px">
        <div class="form-group" style="grid-column:1/-1"><label class="form-label">Firmenname</label>
          <input class="form-control" id="f-name" value="${esc(f.name)}"></div>
        <div class="form-group"><label class="form-label">Rechtsform</label>
          <input class="form-control" id="f-rf" value="${esc(f.rechtsform)}"></div>
        <div class="form-group"><label class="form-label">Rechnungs-Prefix</label>
          <input class="form-control" id="f-pre" value="${esc(f.rechnungs_prefix)}"></div>
        <div class="form-group" style="grid-column:1/-1"><label class="form-label">Adresse (mehrzeilig)</label>
          <textarea class="form-control" id="f-adr" rows="3">${esc(f.adresse)}</textarea></div>
        <div class="form-group"><label class="form-label">Besteuerung</label>
          <select class="form-control" id="f-best">${bestOpts}</select></div>
        <div class="form-group"><label class="form-label">USt-Satz (%)</label>
          <input class="form-control" type="number" id="f-ust" value="${f.ust_satz || 19}" step="0.01"></div>
        <div class="form-group"><label class="form-label">Steuernummer</label>
          <input class="form-control" id="f-steuernr" value="${esc(f.steuernummer)}"></div>
        <div class="form-group"><label class="form-label">USt-IdNr.</label>
          <input class="form-control" id="f-ustid" value="${esc(f.ust_id)}"></div>
        <div class="form-group"><label class="form-label">Standard-Stundensatz (€/Std.)</label>
          <input class="form-control" type="number" step="0.01" id="f-satz" value="${f.stundensatz || ''}"></div>
        <div class="form-group"><label class="form-label">E-Mail</label>
          <input class="form-control" id="f-email" value="${esc(f.email)}"></div>
        <div class="form-group"><label class="form-label">Telefon</label>
          <input class="form-control" id="f-tel" value="${esc(f.telefon)}"></div>
        <div class="form-group"><label class="form-label">Webseite</label>
          <input class="form-control" id="f-web" value="${esc(f.webseite)}"></div>
        <div class="form-group"><label class="form-label">IBAN</label>
          <input class="form-control" id="f-iban" value="${esc(f.iban)}"></div>
        <div class="form-group"><label class="form-label">BIC</label>
          <input class="form-control" id="f-bic" value="${esc(f.bic)}"></div>
        <div class="form-group" style="grid-column:1/-1"><label class="form-label">Bank</label>
          <input class="form-control" id="f-bank" value="${esc(f.bank_name)}"></div>
        <div class="form-group" style="grid-column:1/-1"><label class="form-label">Rechnungs-Fußtext</label>
          <textarea class="form-control" id="f-fuss" rows="3">${esc(f.rechnungs_fusstext)}</textarea></div>
      </div>
      <button class="btn btn-primary" id="f-save" style="margin-top:12px">Speichern</button>
    </div>`;

  document.getElementById('f-save').addEventListener('click', async () => {
    try {
      await api(`/excero/api/web/firmen/${f.id}`, { method: 'PATCH', body: {
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
      }});
      toast('Firmenprofil gespeichert');
      // Topbar aktualisieren
      const nd = await api('/excero/api/web/firmen');
      const nf = (nd.firmen || [])[0];
      if (nf) document.getElementById('topbar-firma').textContent = nf.name;
    } catch (e) { toast(e.message, 'err'); }
  });
}

function esc(s) { return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/"/g,'&quot;'); }
