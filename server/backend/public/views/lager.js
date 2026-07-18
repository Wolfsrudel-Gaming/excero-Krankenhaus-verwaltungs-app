/* ── Lager-Views ────────────────────────────────────────────────────── */

// ── Artikel ─────────────────────────────────────────────────────────
async function viewLagerArtikel() {
  const area = document.getElementById('content-area');
  area.innerHTML = `
    <div class="page-header">
      <span class="page-title">Lager · Artikel</span>
      <span class="page-sub" id="art-count"></span>
    </div>
    <div class="toolbar">
      <input type="search" id="art-suche" placeholder="Suche Bezeichnung / Artikelnr.…">
      <select id="art-kat"><option value="">Alle Kategorien</option></select>
      <span class="spacer"></span>
      ${exportBtn('lager-artikel')}
      <button class="btn btn-primary" id="art-neu">+ Artikel</button>
    </div>
    <div class="grid-wrap"><div id="art-grid"></div></div>`;

  async function lade() {
    const q = document.getElementById('art-suche').value;
    const kat = document.getElementById('art-kat').value;
    const data = await api(`/kkh/api/web/lager/artikel?suche=${encodeURIComponent(q)}&kategorie=${encodeURIComponent(kat)}`);
    if (!data) return;
    const { artikel = [], kategorien = [] } = data;
    document.getElementById('art-count').textContent = `${artikel.length} Artikel`;

    const katSel = document.getElementById('art-kat');
    const aktuellKat = katSel.value;
    katSel.innerHTML = '<option value="">Alle Kategorien</option>';
    kategorien.forEach((k) => {
      const o = document.createElement('option');
      o.value = k; o.textContent = k;
      if (k === aktuellKat) o.selected = true;
      katSel.appendChild(o);
    });

    new DataGrid('art-grid', {
      columns: [
        { key: 'bezeichnung', label: 'Bezeichnung' },
        { key: 'artikelnummer', label: 'Artikelnr.', cls: 'mono' },
        { key: 'kategorie', label: 'Kategorie' },
        { key: 'einheit', label: 'Einh.' },
        { key: 'bestand', label: 'Bestand', num: true, render: (r) => {
          const cls = r.mindestbestand > 0 && r.bestand <= r.mindestbestand
            ? (r.bestand <= 0 ? 'badge-err' : 'badge-warn') : '';
          return cls ? `<span class="badge ${cls}">${fmt(r.bestand, 'num')}</span>` : fmt(r.bestand, 'num');
        }},
        { key: 'mindestbestand', label: 'MindestB.', num: true },
        { key: 'ek_preis', label: 'EK-Preis', num: true, numFmt: 'eur' },
        { key: 'vk_preis', label: 'VK-Preis', num: true, numFmt: 'eur' },
        { key: 'lieferant_name', label: 'Lieferant' },
      ],
      rows: artikel,
      rowClass: (r) => r.mindestbestand > 0 && r.bestand <= 0 ? 'crit-row' :
        r.mindestbestand > 0 && r.bestand <= r.mindestbestand ? 'warn-row' : '',
      actions: (r) => `
        <button class="btn btn-secondary btn-sm" onclick="modalArtikelBearbeiten(${r.id})">Bearb.</button>
        <button class="btn btn-primary btn-sm" onclick="modalBuchungNeu(${r.id}, '${escH(r.bezeichnung)}', '${escH(r.einheit)}')">Buchen</button>
        <button class="btn btn-danger btn-sm" onclick="loescheArtikel(${r.id})">Löschen</button>`,
    });
  }

  await lade();
  document.getElementById('art-suche').addEventListener('input', () => lade());
  document.getElementById('art-kat').addEventListener('change', () => lade());
  document.getElementById('art-neu').addEventListener('click', () => modalArtikelNeu(lade));
}

async function holelieferanten() {
  const d = await api('/kkh/api/web/lieferanten').catch(() => null);
  return (d?.lieferanten || []).filter((l) => l.aktiv);
}

async function modalArtikelNeu(reload) {
  const lief = await holelieferanten();
  const liefOpts = lief.map((l) => `<option value="${l.id}">${escH(l.name)}</option>`).join('');
  const res = await modal('Neuer Artikel', `
    <div class="form-grid">
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Bezeichnung <span class="req">*</span></label>
        <input class="form-control" id="m-bez" placeholder="z. B. HDMI-Kabel 2m"></div>
      <div class="form-group"><label class="form-label">Artikelnummer / SKU</label>
        <input class="form-control" id="m-artnr"></div>
      <div class="form-group"><label class="form-label">Kategorie</label>
        <input class="form-control" id="m-kat" placeholder="z. B. Kabel"></div>
      <div class="form-group"><label class="form-label">Einheit</label>
        <input class="form-control" id="m-einh" value="Stk."></div>
      <div class="form-group"><label class="form-label">EK-Preis (€)</label>
        <input type="number" step="0.01" class="form-control" id="m-ek"></div>
      <div class="form-group"><label class="form-label">VK-Preis (€)</label>
        <input type="number" step="0.01" class="form-control" id="m-vk"></div>
      <div class="form-group"><label class="form-label">Anfangsbestand</label>
        <input type="number" step="0.01" class="form-control" id="m-best" value="0"></div>
      <div class="form-group"><label class="form-label">Mindestbestand</label>
        <input type="number" step="0.01" class="form-control" id="m-mind" value="0"></div>
      <div class="form-group"><label class="form-label">Lieferant</label>
        <select class="form-control" id="m-lief"><option value="">– kein –</option>${liefOpts}</select></div>
      <div class="form-group"><label class="form-label">App-Material-Name</label>
        <input class="form-control" id="m-app" placeholder="Name aus Prüfbogen"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="m-notiz" rows="2"></textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    await api('/kkh/api/web/lager/artikel', { method: 'POST', body: {
      bezeichnung: document.getElementById('m-bez')?.value?.trim(),
      artikelnummer: document.getElementById('m-artnr')?.value || '',
      kategorie: document.getElementById('m-kat')?.value || '',
      einheit: document.getElementById('m-einh')?.value || 'Stk.',
      ek_preis: document.getElementById('m-ek')?.value || null,
      vk_preis: document.getElementById('m-vk')?.value || null,
      bestand: Number(document.getElementById('m-best')?.value) || 0,
      mindestbestand: Number(document.getElementById('m-mind')?.value) || 0,
      lieferant_id: document.getElementById('m-lief')?.value || null,
      app_material_name: document.getElementById('m-app')?.value || '',
      notiz: document.getElementById('m-notiz')?.value || '',
    }});
    toast('Artikel angelegt');
    reload();
  } catch (e) { toast(e.message, 'err'); }
}

window.modalArtikelBearbeiten = async function(id) {
  const data = await api('/kkh/api/web/lager/artikel');
  const r = data?.artikel?.find((a) => a.id === id);
  if (!r) return;
  const lief = await holelieferanten();
  const liefOpts = lief.map((l) =>
    `<option value="${l.id}" ${l.id === r.lieferant_id ? 'selected' : ''}>${escH(l.name)}</option>`).join('');
  const res = await modal(`Artikel bearbeiten: ${r.bezeichnung}`, `
    <div class="form-grid">
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Bezeichnung <span class="req">*</span></label>
        <input class="form-control" id="m-bez" value="${escH(r.bezeichnung)}"></div>
      <div class="form-group"><label class="form-label">Artikelnummer / SKU</label>
        <input class="form-control" id="m-artnr" value="${escH(r.artikelnummer)}"></div>
      <div class="form-group"><label class="form-label">Kategorie</label>
        <input class="form-control" id="m-kat" value="${escH(r.kategorie)}"></div>
      <div class="form-group"><label class="form-label">Einheit</label>
        <input class="form-control" id="m-einh" value="${escH(r.einheit)}"></div>
      <div class="form-group"><label class="form-label">EK-Preis (€)</label>
        <input type="number" step="0.01" class="form-control" id="m-ek" value="${r.ek_preis ?? ''}"></div>
      <div class="form-group"><label class="form-label">VK-Preis (€)</label>
        <input type="number" step="0.01" class="form-control" id="m-vk" value="${r.vk_preis ?? ''}"></div>
      <div class="form-group"><label class="form-label">Mindestbestand</label>
        <input type="number" step="0.01" class="form-control" id="m-mind" value="${r.mindestbestand}"></div>
      <div class="form-group"><label class="form-label">Lieferant</label>
        <select class="form-control" id="m-lief"><option value="">– kein –</option>${liefOpts}</select></div>
      <div class="form-group"><label class="form-label">App-Material-Name</label>
        <input class="form-control" id="m-app" value="${escH(r.app_material_name)}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="m-notiz" rows="2">${escH(r.notiz)}</textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    await api(`/kkh/api/web/lager/artikel/${id}`, { method: 'PATCH', body: {
      bezeichnung: document.getElementById('m-bez')?.value?.trim(),
      artikelnummer: document.getElementById('m-artnr')?.value || '',
      kategorie: document.getElementById('m-kat')?.value || '',
      einheit: document.getElementById('m-einh')?.value || 'Stk.',
      ek_preis: document.getElementById('m-ek')?.value || null,
      vk_preis: document.getElementById('m-vk')?.value || null,
      mindestbestand: Number(document.getElementById('m-mind')?.value) || 0,
      lieferant_id: document.getElementById('m-lief')?.value || null,
      app_material_name: document.getElementById('m-app')?.value || '',
      notiz: document.getElementById('m-notiz')?.value || '',
    }});
    toast('Artikel gespeichert');
    viewLagerArtikel();
  } catch (e) { toast(e.message, 'err'); }
};

window.loescheArtikel = async function(id) {
  if (!(await confirm('Artikel wirklich deaktivieren?'))) return;
  try { await api(`/kkh/api/web/lager/artikel/${id}`, { method: 'DELETE' }); toast('Deaktiviert'); viewLagerArtikel(); }
  catch (e) { toast(e.message, 'err'); }
};

// ── Buchung (Modal) ──────────────────────────────────────────────────
window.modalBuchungNeu = async function(artikelId, bezeichnung, einheit) {
  const res = await modal(`Buchung: ${bezeichnung}`, `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Typ <span class="req">*</span></label>
        <select class="form-control" id="b-typ">
          <option value="eingang">Eingang (+)</option>
          <option value="ausgang">Ausgang (−)</option>
          <option value="korrektur">Korrektur (= Bestand setzen)</option>
        </select></div>
      <div class="form-group"><label class="form-label">Menge (${escH(einheit)}) <span class="req">*</span></label>
        <input type="number" step="0.01" class="form-control" id="b-menge" min="0.01" value="1"></div>
      <div class="form-group"><label class="form-label">EK-Preis (€) optional</label>
        <input type="number" step="0.01" class="form-control" id="b-ek"></div>
      <div class="form-group"><label class="form-label">Bezug (Auftrag/Zimmer)</label>
        <input class="form-control" id="b-bezug"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Grund</label>
        <input class="form-control" id="b-grund"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Buchen', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    await api('/kkh/api/web/lager/buchung', { method: 'POST', body: {
      artikel_id: artikelId,
      typ: document.getElementById('b-typ')?.value,
      menge: Number(document.getElementById('b-menge')?.value),
      ek_preis: document.getElementById('b-ek')?.value || null,
      bezug: document.getElementById('b-bezug')?.value || '',
      grund: document.getElementById('b-grund')?.value || '',
    }});
    toast('Buchung gespeichert');
    viewLagerArtikel();
  } catch (e) { toast(e.message, 'err'); }
};

// ── Buchungen-View ───────────────────────────────────────────────────
async function viewLagerBuchungen() {
  const area = document.getElementById('content-area');
  const vonDef = vor30T(), bisDef = heute();
  area.innerHTML = `
    <div class="page-header"><span class="page-title">Lager · Buchungen</span></div>
    <div class="toolbar">
      <input type="search" id="buch-suche" placeholder="Suche Artikel / Grund / Bezug…">
      <label style="font-size:12px;color:var(--muted)">Von</label>
      <input type="date" id="buch-von" value="${vonDef}">
      <label style="font-size:12px;color:var(--muted)">Bis</label>
      <input type="date" id="buch-bis" value="${bisDef}">
      <span class="spacer"></span>
      <div id="buch-export"></div>
    </div>
    <div class="grid-wrap"><div id="buch-grid"></div></div>`;

  async function lade() {
    const von = document.getElementById('buch-von').value;
    const bis = document.getElementById('buch-bis').value;
    document.getElementById('buch-export').innerHTML = exportBtn('buchungen', `von=${von}&bis=${bis}`);
    const data = await api(`/kkh/api/web/lager/buchungen?von=${von}&bis=${bis}&limit=500`);
    if (!data) return;
    let rows = data.buchungen || [];
    const q = document.getElementById('buch-suche').value.toLowerCase();
    if (q) rows = rows.filter((b) => [b.bezeichnung, b.grund, b.bezug, b.benutzer]
      .some((v) => String(v || '').toLowerCase().includes(q)));
    new DataGrid('buch-grid', {
      columns: [
        { key: 'zeitpunkt', label: 'Zeitpunkt', cls: 'mono', render: (r) => escH(new Date(r.zeitpunkt).toLocaleString('de-DE')) },
        { key: 'bezeichnung', label: 'Artikel' },
        { key: 'typ', label: 'Typ', render: (r) => {
          const cls = r.typ === 'eingang' ? 'badge-ok' : r.typ === 'ausgang' ? 'badge-warn' : 'badge-info';
          return `<span class="badge ${cls}">${escH(r.typ)}</span>`;
        }},
        { key: 'menge', label: 'Menge', num: true },
        { key: 'einheit', label: 'Einh.' },
        { key: 'ek_preis', label: 'EK €', num: true, numFmt: 'eur' },
        { key: 'bezug', label: 'Bezug' },
        { key: 'grund', label: 'Grund' },
        { key: 'benutzer', label: 'Benutzer' },
      ],
      rows,
    });
  }
  await lade();
  document.getElementById('buch-suche').addEventListener('input', lade);
  ['buch-von', 'buch-bis'].forEach((id) => document.getElementById(id).addEventListener('change', lade));
}

// ── Verbrauch aus Prüfungen ──────────────────────────────────────────
async function viewLagerVerbrauch() {
  const area = document.getElementById('content-area');
  const vonDef = vor30T(), bisDef = heute();
  area.innerHTML = `
    <div class="page-header"><span class="page-title">Lager · Verbrauch aus Prüfungen</span></div>
    <div class="toolbar">
      <label style="font-size:12px;color:var(--muted)">Von</label>
      <input type="date" id="vb-von" value="${vonDef}">
      <label style="font-size:12px;color:var(--muted)">Bis</label>
      <input type="date" id="vb-bis" value="${bisDef}">
      <input type="text" id="vb-sta" placeholder="Station (leer = alle)">
      <button class="btn btn-primary btn-sm" id="vb-laden">Laden</button>
      <span class="spacer"></span>
      <div id="vb-export"></div>
    </div>
    <div class="grid-wrap"><div id="vb-grid"></div></div>`;

  async function lade() {
    const von = document.getElementById('vb-von').value;
    const bis = document.getElementById('vb-bis').value;
    const sta = document.getElementById('vb-sta').value;
    document.getElementById('vb-export').innerHTML = exportBtn('pruefungen', `von=${von}&bis=${bis}`);
    const data = await api(`/kkh/api/web/lager/verbrauch?von=${von}&bis=${bis}&station=${encodeURIComponent(sta)}`);
    if (!data) return;
    new DataGrid('vb-grid', {
      columns: [
        { key: 'station', label: 'Station' },
        { key: 'material', label: 'Material / Arbeit' },
        { key: 'anzahl', label: 'Anzahl', num: true },
        { key: 'ek_preis', label: 'EK-Preis', num: true, numFmt: 'eur' },
        { key: 'gesamt_ek', label: 'Gesamt EK', num: true, numFmt: 'eur' },
        { key: '_linked', label: 'Artikel verknüpft', render: (r) =>
          r.ek_preis ? '<span class="badge badge-ok">✔ Verknüpft</span>' : '<span class="badge badge-muted">–</span>' },
      ],
      rows: data.verbrauch || [],
    });
  }
  await lade();
  document.getElementById('vb-laden').addEventListener('click', lade);
}

// ── Nachbestellung ───────────────────────────────────────────────────
async function viewLagerNachbestellung() {
  const area = document.getElementById('content-area');
  area.innerHTML = `
    <div class="page-header">
      <span class="page-title">Lager · Nachbestellung</span>
      <span class="page-sub" id="nb-count"></span>
    </div>
    <div class="toolbar"><span class="spacer"></span>${exportBtn('lager-artikel', 'nachbestellung=1')}</div>
    <div class="grid-wrap"><div id="nb-grid"></div></div>`;

  const data = await api('/kkh/api/web/lager/nachbestellung');
  if (!data) return;
  const rows = data.artikel || [];
  document.getElementById('nb-count').textContent =
    rows.length === 0 ? 'Alles ausreichend!' : `${rows.length} Artikel unter Mindestbestand`;
  const badge = document.getElementById('nb-badge');
  if (badge) { badge.textContent = rows.length; badge.hidden = rows.length === 0; }

  if (rows.length === 0) {
    document.getElementById('nb-grid').innerHTML = '<div class="no-data" style="padding:30px;">✔ Alle Artikel haben ausreichenden Bestand.</div>';
    return;
  }
  new DataGrid('nb-grid', {
    columns: [
      { key: 'bezeichnung', label: 'Bezeichnung' },
      { key: 'kategorie', label: 'Kategorie' },
      { key: 'bestand', label: 'Bestand', num: true, render: (r) =>
        `<span class="badge ${r.bestand <= 0 ? 'badge-err' : 'badge-warn'}">${fmt(r.bestand, 'num')}</span>` },
      { key: 'mindestbestand', label: 'Mindestbestand', num: true },
      { key: 'einheit', label: 'Einh.' },
      { key: 'lieferant_name', label: 'Lieferant' },
      { key: 'ek_preis', label: 'EK-Preis', num: true, numFmt: 'eur' },
    ],
    rows,
    rowClass: (r) => r.bestand <= 0 ? 'crit-row' : 'warn-row',
    actions: (r) => `<button class="btn btn-primary btn-sm" onclick="modalBuchungNeu(${r.id},'${escH(r.bezeichnung)}','${escH(r.einheit)}')">Eingang buchen</button>`,
  });
}

// ── Lieferanten ──────────────────────────────────────────────────────
async function viewLieferanten() {
  const area = document.getElementById('content-area');
  area.innerHTML = `
    <div class="page-header">
      <span class="page-title">Lieferanten</span>
      <span class="page-sub" id="lf-count"></span>
    </div>
    <div class="toolbar">
      <input type="search" id="lf-suche" placeholder="Suche Name / Kontakt / Kundennr.…">
      <span class="spacer"></span>
      <button class="btn btn-primary" id="lf-neu">+ Lieferant</button>
    </div>
    <div class="grid-wrap"><div id="lf-grid"></div></div>`;

  async function lade() {
    const data = await api('/kkh/api/web/lieferanten');
    if (!data) return;
    let rows = data.lieferanten || [];
    const q = document.getElementById('lf-suche').value.toLowerCase();
    if (q) rows = rows.filter((l) => [l.name, l.kontakt, l.kundennummer, l.email]
      .some((v) => String(v || '').toLowerCase().includes(q)));
    document.getElementById('lf-count').textContent = `${rows.length} Lieferanten`;
    new DataGrid('lf-grid', {
      columns: [
        { key: 'name', label: 'Name' },
        { key: 'kontakt', label: 'Kontakt' },
        { key: 'telefon', label: 'Telefon' },
        { key: 'email', label: 'E-Mail' },
        { key: 'kundennummer', label: 'Kundennr.' },
        { key: 'aktiv', label: 'Status', render: (r) =>
          r.aktiv ? '<span class="badge badge-ok">Aktiv</span>' : '<span class="badge badge-muted">Inaktiv</span>' },
      ],
      rows,
      actions: (r) => `
        <button class="btn btn-secondary btn-sm" onclick="modalLieferantBearbeiten(${r.id})">Bearb.</button>
        <button class="btn btn-danger btn-sm" onclick="loescheLieferant(${r.id})">Deaktivieren</button>`,
    });
  }
  await lade();
  document.getElementById('lf-suche').addEventListener('input', lade);
  document.getElementById('lf-neu').addEventListener('click', () => modalLieferantNeu(lade));
}

async function modalLieferantNeu(reload) {
  const res = await modal('Neuer Lieferant', `
    <div class="form-grid">
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Name <span class="req">*</span></label>
        <input class="form-control" id="lf-name"></div>
      <div class="form-group"><label class="form-label">Ansprechpartner</label>
        <input class="form-control" id="lf-kontakt"></div>
      <div class="form-group"><label class="form-label">Telefon</label>
        <input class="form-control" id="lf-tel"></div>
      <div class="form-group"><label class="form-label">E-Mail</label>
        <input type="email" class="form-control" id="lf-mail"></div>
      <div class="form-group"><label class="form-label">Kundennummer</label>
        <input class="form-control" id="lf-knr"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="lf-notiz" rows="2"></textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    await api('/kkh/api/web/lieferanten', { method: 'POST', body: {
      name: document.getElementById('lf-name')?.value?.trim(),
      kontakt: document.getElementById('lf-kontakt')?.value || '',
      telefon: document.getElementById('lf-tel')?.value || '',
      email: document.getElementById('lf-mail')?.value || '',
      kundennummer: document.getElementById('lf-knr')?.value || '',
      notiz: document.getElementById('lf-notiz')?.value || '',
    }});
    toast('Lieferant angelegt');
    reload();
  } catch (e) { toast(e.message, 'err'); }
}

window.modalLieferantBearbeiten = async function(id) {
  const data = await api('/kkh/api/web/lieferanten');
  const r = data?.lieferanten?.find((l) => l.id === id);
  if (!r) return;
  const res = await modal(`Lieferant: ${r.name}`, `
    <div class="form-grid">
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Name <span class="req">*</span></label>
        <input class="form-control" id="lf-name" value="${escH(r.name)}"></div>
      <div class="form-group"><label class="form-label">Ansprechpartner</label>
        <input class="form-control" id="lf-kontakt" value="${escH(r.kontakt)}"></div>
      <div class="form-group"><label class="form-label">Telefon</label>
        <input class="form-control" id="lf-tel" value="${escH(r.telefon)}"></div>
      <div class="form-group"><label class="form-label">E-Mail</label>
        <input type="email" class="form-control" id="lf-mail" value="${escH(r.email)}"></div>
      <div class="form-group"><label class="form-label">Kundennummer</label>
        <input class="form-control" id="lf-knr" value="${escH(r.kundennummer)}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="lf-notiz" rows="2">${escH(r.notiz)}</textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    await api(`/kkh/api/web/lieferanten/${id}`, { method: 'PATCH', body: {
      name: document.getElementById('lf-name')?.value?.trim(),
      kontakt: document.getElementById('lf-kontakt')?.value || '',
      telefon: document.getElementById('lf-tel')?.value || '',
      email: document.getElementById('lf-mail')?.value || '',
      kundennummer: document.getElementById('lf-knr')?.value || '',
      notiz: document.getElementById('lf-notiz')?.value || '',
    }});
    toast('Lieferant gespeichert');
    viewLieferanten();
  } catch (e) { toast(e.message, 'err'); }
};

window.loescheLieferant = async function(id) {
  if (!(await confirm('Lieferant wirklich deaktivieren?'))) return;
  try { await api(`/kkh/api/web/lieferanten/${id}`, { method: 'DELETE' }); toast('Deaktiviert'); viewLieferanten(); }
  catch (e) { toast(e.message, 'err'); }
};

// ── Abrechnung ───────────────────────────────────────────────────────
async function viewAbrechnung() {
  const area = document.getElementById('content-area');
  const vonDef = vor30T(), bisDef = heute();
  area.innerHTML = `
    <div class="page-header"><span class="page-title">Abrechnung &amp; Auswertung</span></div>
    <div class="toolbar">
      <label style="font-size:12px;color:var(--muted)">Zeitraum von</label>
      <input type="date" id="ab-von" value="${vonDef}">
      <label style="font-size:12px;color:var(--muted)">bis</label>
      <input type="date" id="ab-bis" value="${bisDef}">
      <button class="btn btn-primary btn-sm" id="ab-laden">Auswerten</button>
      <span class="spacer"></span>
      <div id="ab-export"></div>
    </div>
    <div class="kpi-grid" id="ab-kpi"></div>
    <div class="card">
      <div class="card-title">📋 Material-/Arbeitsabrechnung</div>
      <div class="grid-wrap"><div id="ab-grid"></div></div>
    </div>`;

  async function lade() {
    const von = document.getElementById('ab-von').value;
    const bis = document.getElementById('ab-bis').value;
    document.getElementById('ab-export').innerHTML = exportBtn('abrechnung', `von=${von}&bis=${bis}`);
    const data = await api(`/kkh/api/web/lager/verbrauch?von=${von}&bis=${bis}`);
    if (!data) return;
    const rows = data.verbrauch || [];
    const gesamt = rows.reduce((s, r) => s + (Number(r.gesamt_ek) || 0), 0);
    const anzahl = rows.reduce((s, r) => s + (Number(r.anzahl) || 0), 0);
    document.getElementById('ab-kpi').innerHTML = `
      <div class="kpi-card"><div class="kpi-val">${rows.length}</div><div class="kpi-label">Positionen</div></div>
      <div class="kpi-card info"><div class="kpi-val">${anzahl}</div><div class="kpi-label">Einsätze gesamt</div></div>
      <div class="kpi-card ok"><div class="kpi-val">${gesamt.toLocaleString('de-DE',{style:'currency',currency:'EUR'})}</div><div class="kpi-label">Materialwert EK</div></div>`;
    new DataGrid('ab-grid', {
      columns: [
        { key: 'station', label: 'Station' },
        { key: 'material', label: 'Material / Arbeit' },
        { key: 'anzahl', label: 'Anzahl', num: true },
        { key: 'ek_preis', label: 'EK-Preis', num: true, numFmt: 'eur' },
        { key: 'gesamt_ek', label: 'Gesamt EK', num: true, numFmt: 'eur' },
      ],
      rows,
    });
  }
  await lade();
  document.getElementById('ab-laden').addEventListener('click', lade);
}

// ── Mitarbeiter ──────────────────────────────────────────────────────
async function viewMitarbeiter() {
  const area = document.getElementById('content-area');
  area.innerHTML = `
    <div class="page-header"><span class="page-title">Mitarbeiter</span></div>
    <div class="toolbar">
      <button class="btn btn-primary" id="ma-neu">+ Mitarbeiter</button>
    </div>
    <div class="grid-wrap"><div id="ma-grid"></div></div>`;

  async function lade() {
    const data = await api('/kkh/api/web/mitarbeiter');
    if (!data) return;
    new DataGrid('ma-grid', {
      columns: [
        { key: 'name', label: 'Name' },
        { key: 'aktiv', label: 'Status', render: (r) =>
          r.aktiv ? '<span class="badge badge-ok">Aktiv</span>' : '<span class="badge badge-muted">Inaktiv</span>' },
      ],
      rows: data.mitarbeiter || [],
      actions: (r) => `
        <button class="btn btn-secondary btn-sm" onclick="toggleMitarbeiter('${escH(r.name)}',${!r.aktiv},'${escH(r.aktiv ? 'inaktiv' : 'aktiv')}')">
          ${r.aktiv ? 'Deaktivieren' : 'Aktivieren'}
        </button>`,
    });
  }
  await lade();
  document.getElementById('ma-neu').addEventListener('click', async () => {
    const res = await modal('Mitarbeiter anlegen', `
      <div class="form-group"><label class="form-label">Name <span class="req">*</span></label>
        <input class="form-control" id="ma-name"></div>`,
      [{ label: 'Abbrechen', value: null }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
    if (res !== 'ok') return;
    try {
      await api('/kkh/api/web/mitarbeiter', { method: 'POST', body: { name: document.getElementById('ma-name')?.value?.trim() }});
      toast('Mitarbeiter angelegt');
      lade();
    } catch (e) { toast(e.message, 'err'); }
  });
}

window.toggleMitarbeiter = async function(name, aktiv) {
  try {
    await api(`/kkh/api/web/mitarbeiter/${encodeURIComponent(name)}`, { method: 'PATCH', body: { aktiv }});
    toast('Gespeichert');
    viewMitarbeiter();
  } catch (e) { toast(e.message, 'err'); }
};
