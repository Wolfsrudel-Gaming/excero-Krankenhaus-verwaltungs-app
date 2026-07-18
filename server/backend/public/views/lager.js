/**
 * Lager-Views – KKH TV-Wartung
 * Enthält auch: viewAbrechnung, viewMitarbeiter
 * Komplett neu mit korrektem DataGrid-API und modal/mf
 */

/* ─── Hilfsfunktionen ─────────────────────────────────────────────────── */
function vor30T() {
  const d = new Date(Date.now() - 30 * 86400e3);
  return d.toISOString().slice(0, 10);
}

async function holeLieferanten() {
  const d = await api('/kkh/api/web/lieferanten').catch(() => null);
  return (d?.lieferanten || []).filter((l) => l.aktiv);
}

/* ─── Artikel ─────────────────────────────────────────────────────────── */
async function viewLagerArtikel() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Lager · Artikel', `
      <a href="/kkh/api/web/export/lager-artikel" target="_blank" class="btn btn-secondary btn-sm">📊 Export</a>
      <button class="btn btn-primary" id="art-neu">+ Artikel</button>
    `)}
    <div class="filter-bar">
      <div class="filter-group"><label>Kategorie</label>
        <select class="form-control form-control-sm" id="art-kat">
          <option value="">Alle Kategorien</option>
        </select></div>
      <div class="filter-group"><label>Status</label>
        <select class="form-control form-control-sm" id="art-status">
          <option value="aktiv">Nur aktive</option>
          <option value="">Alle</option>
        </select></div>
    </div>
    <div id="art-container"><div class="loading">Wird geladen…</div></div>
  `;

  let alleArtikel = [];

  async function lade() {
    const container = document.getElementById('art-container');
    if (!container) return;
    const kat = document.getElementById('art-kat')?.value || '';
    const status = document.getElementById('art-status')?.value;

    let data;
    try { data = await api(`/kkh/api/web/lager/artikel?suche=&kategorie=${encodeURIComponent(kat)}`); }
    catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    alleArtikel = data?.artikel || [];
    const kategorien = data?.kategorien || [];

    const katSel = document.getElementById('art-kat');
    if (katSel) {
      const aktuell = katSel.value;
      katSel.innerHTML = '<option value="">Alle Kategorien</option>';
      kategorien.forEach((k) => {
        const o = document.createElement('option');
        o.value = k; o.textContent = k;
        if (k === aktuell) o.selected = true;
        katSel.appendChild(o);
      });
    }

    let rows = [...alleArtikel];
    if (status === 'aktiv') rows = rows.filter((r) => !r.inaktiv);

    new DataGrid(container, {
      data: rows,
      filterKeys: ['bezeichnung', 'artikelnummer', 'kategorie', 'lieferant_name'],
      columns: [
        { key: 'bezeichnung', label: 'Bezeichnung', sort: true },
        { key: 'artikelnummer', label: 'Artikelnr.', sort: true, width: '110px',
          render: (v) => v ? `<code class="mono">${escH(v)}</code>` : '–' },
        { key: 'kategorie', label: 'Kategorie', sort: true, width: '120px',
          render: (v) => v ? badge(v, 'teal') : '–' },
        { key: 'einheit', label: 'Einh.', width: '60px', align: 'center' },
        { key: 'bestand', label: 'Bestand', sort: true, width: '100px', align: 'right',
          render: (v, row) => {
            const n = Number(v || 0);
            if (row.mindestbestand > 0 && n <= 0)
              return badge(String(n), 'err');
            if (row.mindestbestand > 0 && n <= row.mindestbestand)
              return badge(String(n), 'warn');
            return `<strong>${n}</strong>`;
          }},
        { key: 'mindestbestand', label: 'Min.B.', width: '70px', align: 'right' },
        { key: 'ek_preis', label: 'EK €', sort: true, width: '90px', align: 'right',
          render: (v) => v != null ? fmtEur(v) : '–' },
        { key: 'vk_preis', label: 'VK €', sort: true, width: '90px', align: 'right',
          render: (v) => v != null ? fmtEur(v) : '–' },
        { key: 'lieferant_name', label: 'Lieferant', sort: true },
        { key: '_actions', label: '', width: '160px', align: 'right',
          render: (v, row) => `
            <button class="btn btn-xs btn-secondary art-edit" data-id="${row.id}">✏</button>
            <button class="btn btn-xs btn-primary art-buchen" data-id="${row.id}" data-name="${escH(row.bezeichnung)}" data-einh="${escH(row.einheit || 'Stk.')}">+ Buchen</button>
            <button class="btn btn-xs btn-danger art-del" data-id="${row.id}">🗑</button>
          ` },
      ],
    });

    container.addEventListener('click', async (e) => {
      const editBtn = e.target.closest('.art-edit');
      const buchenBtn = e.target.closest('.art-buchen');
      const delBtn = e.target.closest('.art-del');
      if (editBtn) await modalArtikelBearbeiten(Number(editBtn.dataset.id), lade);
      else if (buchenBtn) await modalBuchungNeu(Number(buchenBtn.dataset.id), buchenBtn.dataset.name, buchenBtn.dataset.einh, lade);
      else if (delBtn) {
        if (!(await confirm('Artikel wirklich deaktivieren?'))) return;
        try { await api(`/kkh/api/web/lager/artikel/${delBtn.dataset.id}`, { method: 'DELETE' }); toast('Deaktiviert'); lade(); }
        catch (err) { toast(err.message, 'err'); }
      }
    });
  }

  document.getElementById('art-neu').addEventListener('click', () => modalArtikelNeu(lade));
  document.getElementById('art-kat').addEventListener('change', lade);
  document.getElementById('art-status').addEventListener('change', lade);

  await lade();
}

async function modalArtikelNeu(reload) {
  const lief = await holeLieferanten();
  const liefOpts = lief.map((l) => `<option value="${l.id}">${escH(l.name)}</option>`).join('');
  const res = await modal('Neuer Artikel', `
    <div class="form-grid">
      <div class="form-group full"><label class="form-label">Bezeichnung *</label>
        <input class="form-control" id="m-bez" placeholder="z.B. HDMI-Kabel 2m"></div>
      <div class="form-group"><label class="form-label">Artikelnummer / SKU</label>
        <input class="form-control" id="m-artnr"></div>
      <div class="form-group"><label class="form-label">Kategorie</label>
        <input class="form-control" id="m-kat" placeholder="z.B. Kabel"></div>
      <div class="form-group"><label class="form-label">Einheit</label>
        <input class="form-control" id="m-einh" value="Stk."></div>
      <div class="form-group"><label class="form-label">EK-Preis (€)</label>
        <input type="number" step="0.01" class="form-control" id="m-ek" placeholder="0.00"></div>
      <div class="form-group"><label class="form-label">VK-Preis (€)</label>
        <input type="number" step="0.01" class="form-control" id="m-vk" placeholder="0.00"></div>
      <div class="form-group"><label class="form-label">Anfangsbestand</label>
        <input type="number" step="0.01" class="form-control" id="m-best" value="0"></div>
      <div class="form-group"><label class="form-label">Mindestbestand</label>
        <input type="number" step="0.01" class="form-control" id="m-mind" value="0"></div>
      <div class="form-group"><label class="form-label">Lieferant</label>
        <select class="form-control" id="m-lief"><option value="">– kein –</option>${liefOpts}</select></div>
      <div class="form-group"><label class="form-label">App-Material-Name</label>
        <input class="form-control" id="m-app" placeholder="Name aus Prüfbogen für Verbrauchsverknüpfung"></div>
      <div class="form-group full"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="m-notiz" rows="2"></textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  const bez = mf(res, 'm-bez')?.trim();
  if (!bez) { toast('Bezeichnung eingeben', 'err'); return; }
  try {
    await api('/kkh/api/web/lager/artikel', { method: 'POST', body: {
      bezeichnung: bez,
      artikelnummer: mf(res, 'm-artnr') || '',
      kategorie: mf(res, 'm-kat') || '',
      einheit: mf(res, 'm-einh') || 'Stk.',
      ek_preis: mf(res, 'm-ek') || null,
      vk_preis: mf(res, 'm-vk') || null,
      bestand: Number(mf(res, 'm-best')) || 0,
      mindestbestand: Number(mf(res, 'm-mind')) || 0,
      lieferant_id: mf(res, 'm-lief') || null,
      app_material_name: mf(res, 'm-app') || '',
      notiz: mf(res, 'm-notiz') || '',
    }});
    toast('Artikel angelegt');
    reload();
  } catch (e) { toast(e.message, 'err'); }
}

async function modalArtikelBearbeiten(id, reload) {
  const data = await api('/kkh/api/web/lager/artikel');
  const r = data?.artikel?.find((a) => a.id === id);
  if (!r) { toast('Artikel nicht gefunden', 'err'); return; }
  const lief = await holeLieferanten();
  const liefOpts = lief.map((l) =>
    `<option value="${l.id}" ${l.id === r.lieferant_id ? 'selected' : ''}>${escH(l.name)}</option>`).join('');
  const res = await modal(`Artikel: ${r.bezeichnung}`, `
    <div class="form-grid">
      <div class="form-group full"><label class="form-label">Bezeichnung *</label>
        <input class="form-control" id="m-bez" value="${escH(r.bezeichnung)}"></div>
      <div class="form-group"><label class="form-label">Artikelnummer</label>
        <input class="form-control" id="m-artnr" value="${escH(r.artikelnummer || '')}"></div>
      <div class="form-group"><label class="form-label">Kategorie</label>
        <input class="form-control" id="m-kat" value="${escH(r.kategorie || '')}"></div>
      <div class="form-group"><label class="form-label">Einheit</label>
        <input class="form-control" id="m-einh" value="${escH(r.einheit || 'Stk.')}"></div>
      <div class="form-group"><label class="form-label">EK-Preis (€)</label>
        <input type="number" step="0.01" class="form-control" id="m-ek" value="${r.ek_preis ?? ''}"></div>
      <div class="form-group"><label class="form-label">VK-Preis (€)</label>
        <input type="number" step="0.01" class="form-control" id="m-vk" value="${r.vk_preis ?? ''}"></div>
      <div class="form-group"><label class="form-label">Mindestbestand</label>
        <input type="number" step="0.01" class="form-control" id="m-mind" value="${r.mindestbestand ?? 0}"></div>
      <div class="form-group"><label class="form-label">Lieferant</label>
        <select class="form-control" id="m-lief"><option value="">– kein –</option>${liefOpts}</select></div>
      <div class="form-group"><label class="form-label">App-Material-Name</label>
        <input class="form-control" id="m-app" value="${escH(r.app_material_name || '')}"></div>
      <div class="form-group full"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="m-notiz" rows="2">${escH(r.notiz || '')}</textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  try {
    await api(`/kkh/api/web/lager/artikel/${id}`, { method: 'PATCH', body: {
      bezeichnung: mf(res, 'm-bez')?.trim() || r.bezeichnung,
      artikelnummer: mf(res, 'm-artnr') || '',
      kategorie: mf(res, 'm-kat') || '',
      einheit: mf(res, 'm-einh') || 'Stk.',
      ek_preis: mf(res, 'm-ek') || null,
      vk_preis: mf(res, 'm-vk') || null,
      mindestbestand: Number(mf(res, 'm-mind')) || 0,
      lieferant_id: mf(res, 'm-lief') || null,
      app_material_name: mf(res, 'm-app') || '',
      notiz: mf(res, 'm-notiz') || '',
    }});
    toast('Artikel gespeichert');
    if (reload) reload();
  } catch (e) { toast(e.message, 'err'); }
}

/* ─── Buchung (Modal) ─────────────────────────────────────────────────── */
async function modalBuchungNeu(artikelId, bezeichnung, einheit, reload) {
  const res = await modal(`Buchung: ${bezeichnung}`, `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Typ *</label>
        <select class="form-control" id="b-typ">
          <option value="eingang">Eingang (+)</option>
          <option value="ausgang">Ausgang (−)</option>
          <option value="korrektur">Korrektur (= setzen)</option>
        </select></div>
      <div class="form-group"><label class="form-label">Menge (${escH(einheit || 'Stk.')}) *</label>
        <input type="number" step="0.01" class="form-control" id="b-menge" min="0.01" value="1"></div>
      <div class="form-group"><label class="form-label">EK-Preis (€) optional</label>
        <input type="number" step="0.01" class="form-control" id="b-ek" placeholder="0.00"></div>
      <div class="form-group"><label class="form-label">Bezug (Auftrag/Zimmer)</label>
        <input class="form-control" id="b-bezug" placeholder="z.B. KKH-A4_01"></div>
      <div class="form-group full"><label class="form-label">Grund</label>
        <input class="form-control" id="b-grund" placeholder="Kurze Beschreibung"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Buchen', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  try {
    await api('/kkh/api/web/lager/buchung', { method: 'POST', body: {
      artikel_id: artikelId,
      typ: mf(res, 'b-typ'),
      menge: Number(mf(res, 'b-menge')) || 1,
      ek_preis: mf(res, 'b-ek') || null,
      bezug: mf(res, 'b-bezug') || '',
      grund: mf(res, 'b-grund') || '',
    }});
    toast('Buchung gespeichert');
    if (reload) reload();
    ladeNachbestellungsBadge?.();
  } catch (e) { toast(e.message, 'err'); }
}

// Für window.modalBuchungNeu (Nachbestellung-View) Kompatibilität
window.modalBuchungNeu = (id, name, einh) => modalBuchungNeu(id, name, einh, () => viewLagerNachbestellung());

/* ─── Buchungen-View ──────────────────────────────────────────────────── */
async function viewLagerBuchungen() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Lager · Buchungen', `
      <div id="buch-export-btn"></div>
    `)}
    <div class="filter-bar">
      <div class="filter-group"><label>Von</label>
        <input type="date" class="form-control form-control-sm" id="buch-von" value="${vor30T()}"></div>
      <div class="filter-group"><label>Bis</label>
        <input type="date" class="form-control form-control-sm" id="buch-bis" value="${heute()}"></div>
      <div class="filter-group"><label>Typ</label>
        <select class="form-control form-control-sm" id="buch-typ">
          <option value="">Alle Typen</option>
          <option value="eingang">Eingang</option>
          <option value="ausgang">Ausgang</option>
          <option value="korrektur">Korrektur</option>
        </select></div>
      <button class="btn btn-primary btn-sm" id="buch-laden">Laden</button>
    </div>
    <div id="buch-container"><div class="loading">Wird geladen…</div></div>
  `;

  async function lade() {
    const von = document.getElementById('buch-von').value;
    const bis = document.getElementById('buch-bis').value;
    const typ = document.getElementById('buch-typ').value;
    const exportBtn = document.getElementById('buch-export-btn');
    if (exportBtn) exportBtn.innerHTML = `<a href="/kkh/api/web/export/buchungen?von=${von}&bis=${bis}" target="_blank" class="btn btn-secondary btn-sm">📊 Export</a>`;

    const container = document.getElementById('buch-container');
    if (!container) return;
    container.innerHTML = '<div class="loading">Wird geladen…</div>';

    let data;
    try { data = await api(`/kkh/api/web/lager/buchungen?von=${von}&bis=${bis}&limit=1000`); }
    catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    let rows = data?.buchungen || [];
    if (typ) rows = rows.filter((b) => b.typ === typ);

    new DataGrid(container, {
      data: rows,
      filterKeys: ['bezeichnung', 'grund', 'bezug', 'benutzer'],
      columns: [
        { key: 'zeitpunkt', label: 'Zeitpunkt', sort: true, width: '150px',
          render: (v) => v ? new Date(v).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '–' },
        { key: 'bezeichnung', label: 'Artikel', sort: true },
        { key: 'typ', label: 'Typ', width: '90px',
          render: (v) => {
            const m = { eingang: 'ok', ausgang: 'warn', korrektur: 'info' };
            return badge(v || '–', m[v] || 'gray');
          }},
        { key: 'menge', label: 'Menge', sort: true, width: '80px', align: 'right',
          render: (v, row) => `${fmtNum(v, 2)} ${escH(row.einheit || '')}` },
        { key: 'ek_preis', label: 'EK €', width: '90px', align: 'right',
          render: (v) => v != null ? fmtEur(v) : '–' },
        { key: 'bezug', label: 'Bezug' },
        { key: 'grund', label: 'Grund' },
        { key: 'benutzer', label: 'Benutzer', width: '100px' },
      ],
    });
  }

  document.getElementById('buch-laden').addEventListener('click', lade);
  await lade();
}

/* ─── Verbrauch aus Prüfungen ─────────────────────────────────────────── */
async function viewLagerVerbrauch() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Lager · Materialverbrauch aus Prüfungen', '')}
    <div class="filter-bar">
      <div class="filter-group"><label>Von</label>
        <input type="date" class="form-control form-control-sm" id="vb-von" value="${vor30T()}"></div>
      <div class="filter-group"><label>Bis</label>
        <input type="date" class="form-control form-control-sm" id="vb-bis" value="${heute()}"></div>
      <div class="filter-group"><label>Station</label>
        <input type="text" class="form-control form-control-sm" id="vb-sta" placeholder="Alle" style="width:100px"></div>
      <button class="btn btn-primary btn-sm" id="vb-laden">Laden</button>
      <div id="vb-export"></div>
    </div>
    <div id="vb-kpi" class="kpi-grid"></div>
    <div id="vb-container"><div class="loading">Wird geladen…</div></div>
  `;

  async function lade() {
    const von = document.getElementById('vb-von').value;
    const bis = document.getElementById('vb-bis').value;
    const sta = document.getElementById('vb-sta').value;
    const exportEl = document.getElementById('vb-export');
    if (exportEl) exportEl.innerHTML = `<a href="/kkh/api/web/export/pruefungen?von=${von}&bis=${bis}" target="_blank" class="btn btn-secondary btn-sm">📊 Export</a>`;

    const container = document.getElementById('vb-container');
    if (!container) return;
    container.innerHTML = '<div class="loading">Wird geladen…</div>';

    let data;
    try { data = await api(`/kkh/api/web/lager/verbrauch?von=${von}&bis=${bis}&station=${encodeURIComponent(sta)}`); }
    catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    const rows = data?.verbrauch || [];
    const gesamtEk = rows.reduce((s, r) => s + (Number(r.gesamt_ek) || 0), 0);
    const gesamtAnzahl = rows.reduce((s, r) => s + (Number(r.anzahl) || 0), 0);
    const verknuepft = rows.filter((r) => r.ek_preis != null).length;

    const kpiEl = document.getElementById('vb-kpi');
    if (kpiEl) kpiEl.innerHTML = `
      <div class="kpi-card"><div class="kpi-label">Positionen</div><div class="kpi-value">${rows.length}</div></div>
      <div class="kpi-card info"><div class="kpi-label">Einsätze gesamt</div><div class="kpi-value">${gesamtAnzahl}</div></div>
      <div class="kpi-card ok"><div class="kpi-label">Materialwert EK</div><div class="kpi-value" style="font-size:18px">${fmtEur(gesamtEk)}</div></div>
      <div class="kpi-card ${verknuepft < rows.length ? 'warn' : 'ok'}">
        <div class="kpi-label">Verknüpft</div>
        <div class="kpi-value">${verknuepft}/${rows.length}</div>
        <div class="kpi-sub">mit Lagerartikel</div>
      </div>`;

    new DataGrid(container, {
      data: rows,
      filterKeys: ['station', 'material'],
      columns: [
        { key: 'station', label: 'Station', sort: true, width: '90px',
          render: (v) => v ? badge(v, 'teal') : '–' },
        { key: 'material', label: 'Material / Arbeit', sort: true },
        { key: 'anzahl', label: 'Anzahl', sort: true, width: '70px', align: 'right' },
        { key: 'ek_preis', label: 'EK-Preis', sort: true, width: '90px', align: 'right',
          render: (v) => v != null ? fmtEur(v) : badge('Nicht verknüpft', 'gray') },
        { key: 'gesamt_ek', label: 'Gesamt EK', sort: true, width: '100px', align: 'right',
          render: (v) => v != null ? `<strong>${fmtEur(v)}</strong>` : '–' },
      ],
    });
  }

  document.getElementById('vb-laden').addEventListener('click', lade);
  await lade();
}

/* ─── Nachbestellung ──────────────────────────────────────────────────── */
async function viewLagerNachbestellung() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Lager · Nachbestellung', `
      <a href="/kkh/api/web/export/lager-artikel?nachbestellung=1" target="_blank" class="btn btn-secondary btn-sm">📊 Export</a>
    `)}
    <div id="nb-container"><div class="loading">Wird geladen…</div></div>
  `;

  let data;
  try { data = await api('/kkh/api/web/lager/nachbestellung'); }
  catch (e) { document.getElementById('nb-container').innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

  const rows = data?.artikel || [];
  const nbBadge = document.getElementById('nb-badge');
  if (nbBadge) { nbBadge.textContent = rows.length; nbBadge.hidden = rows.length === 0; }

  const container = document.getElementById('nb-container');
  if (rows.length === 0) {
    container.innerHTML = '<div class="alert alert-ok">✅ Alle Artikel haben ausreichenden Bestand. Keine Nachbestellung erforderlich.</div>';
    return;
  }

  new DataGrid(container, {
    data: rows.map((r) => ({ ...r, _rowClass: r.bestand <= 0 ? 'crit-row' : 'warn-row' })),
    filterKeys: ['bezeichnung', 'kategorie', 'lieferant_name'],
    columns: [
      { key: 'bezeichnung', label: 'Bezeichnung', sort: true },
      { key: 'kategorie', label: 'Kategorie', sort: true, width: '110px' },
      { key: 'bestand', label: 'Bestand', sort: true, width: '90px', align: 'right',
        render: (v, row) => badge(String(v), v <= 0 ? 'err' : 'warn') },
      { key: 'mindestbestand', label: 'MindestB.', width: '90px', align: 'right' },
      { key: 'einheit', label: 'Einh.', width: '60px', align: 'center' },
      { key: 'lieferant_name', label: 'Lieferant', sort: true },
      { key: 'ek_preis', label: 'EK-Preis', width: '90px', align: 'right',
        render: (v) => v != null ? fmtEur(v) : '–' },
      { key: '_actions', label: '', width: '130px', align: 'right',
        render: (v, row) => `<button class="btn btn-xs btn-primary nb-buchen" data-id="${row.id}" data-name="${escH(row.bezeichnung)}" data-einh="${escH(row.einheit || 'Stk.')}">+ Eingang</button>` },
    ],
  });

  container.addEventListener('click', async (e) => {
    const btn = e.target.closest('.nb-buchen');
    if (btn) await modalBuchungNeu(Number(btn.dataset.id), btn.dataset.name, btn.dataset.einh, () => viewLagerNachbestellung());
  });
}

/* ─── Lieferanten ─────────────────────────────────────────────────────── */
async function viewLieferanten() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Lieferanten', `
      <button class="btn btn-primary" id="lf-neu">+ Lieferant</button>
    `)}
    <div id="lf-container"><div class="loading">Wird geladen…</div></div>
  `;

  async function lade() {
    const container = document.getElementById('lf-container');
    if (!container) return;
    let data;
    try { data = await api('/kkh/api/web/lieferanten'); }
    catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    new DataGrid(container, {
      data: data?.lieferanten || [],
      filterKeys: ['name', 'kontakt', 'email', 'kundennummer'],
      columns: [
        { key: 'name', label: 'Name', sort: true, render: (v) => `<strong>${escH(v || '–')}</strong>` },
        { key: 'kontakt', label: 'Ansprechpartner', sort: true },
        { key: 'telefon', label: 'Telefon', width: '130px',
          render: (v) => v ? `<a href="tel:${escH(v)}">${escH(v)}</a>` : '–' },
        { key: 'email', label: 'E-Mail',
          render: (v) => v ? `<a href="mailto:${escH(v)}">${escH(v)}</a>` : '–' },
        { key: 'kundennummer', label: 'Kundennr.', width: '110px',
          render: (v) => v ? `<code class="mono">${escH(v)}</code>` : '–' },
        { key: 'aktiv', label: 'Status', width: '80px',
          render: (v) => v ? badge('Aktiv', 'ok') : badge('Inaktiv', 'gray') },
        { key: '_actions', label: '', width: '150px', align: 'right',
          render: (v, row) => `
            <button class="btn btn-xs btn-secondary lf-edit" data-id="${row.id}">✏</button>
            <button class="btn btn-xs btn-danger lf-del" data-id="${row.id}">${row.aktiv ? '⛔' : '✅'}</button>
          ` },
      ],
    });

    container.addEventListener('click', async (e) => {
      const editBtn = e.target.closest('.lf-edit');
      const delBtn = e.target.closest('.lf-del');
      if (editBtn) await modalLieferantBearbeiten(Number(editBtn.dataset.id), lade);
      else if (delBtn) {
        const id = delBtn.dataset.id;
        if (!(await confirm('Lieferant wirklich deaktivieren/aktivieren?'))) return;
        try { await api(`/kkh/api/web/lieferanten/${id}`, { method: 'DELETE' }); toast('Gespeichert'); lade(); }
        catch (err) { toast(err.message, 'err'); }
      }
    });
  }

  document.getElementById('lf-neu').addEventListener('click', () => modalLieferantNeu(lade));
  await lade();
}

async function modalLieferantNeu(reload) {
  const res = await modal('Neuer Lieferant', `
    <div class="form-grid">
      <div class="form-group full"><label class="form-label">Name *</label>
        <input class="form-control" id="lf-name" placeholder="Firmenname"></div>
      <div class="form-group"><label class="form-label">Ansprechpartner</label>
        <input class="form-control" id="lf-kontakt"></div>
      <div class="form-group"><label class="form-label">Telefon</label>
        <input class="form-control" id="lf-tel" type="tel"></div>
      <div class="form-group"><label class="form-label">E-Mail</label>
        <input type="email" class="form-control" id="lf-mail"></div>
      <div class="form-group"><label class="form-label">Kundennummer</label>
        <input class="form-control" id="lf-knr"></div>
      <div class="form-group full"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="lf-notiz" rows="2"></textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  const name = mf(res, 'lf-name')?.trim();
  if (!name) { toast('Name eingeben', 'err'); return; }
  try {
    await api('/kkh/api/web/lieferanten', { method: 'POST', body: {
      name,
      kontakt: mf(res, 'lf-kontakt') || '',
      telefon: mf(res, 'lf-tel') || '',
      email: mf(res, 'lf-mail') || '',
      kundennummer: mf(res, 'lf-knr') || '',
      notiz: mf(res, 'lf-notiz') || '',
    }});
    toast('Lieferant angelegt');
    reload();
  } catch (e) { toast(e.message, 'err'); }
}

async function modalLieferantBearbeiten(id, reload) {
  const data = await api('/kkh/api/web/lieferanten');
  const r = data?.lieferanten?.find((l) => l.id === id);
  if (!r) { toast('Lieferant nicht gefunden', 'err'); return; }
  const res = await modal(`Lieferant: ${r.name}`, `
    <div class="form-grid">
      <div class="form-group full"><label class="form-label">Name *</label>
        <input class="form-control" id="lf-name" value="${escH(r.name || '')}"></div>
      <div class="form-group"><label class="form-label">Ansprechpartner</label>
        <input class="form-control" id="lf-kontakt" value="${escH(r.kontakt || '')}"></div>
      <div class="form-group"><label class="form-label">Telefon</label>
        <input class="form-control" id="lf-tel" value="${escH(r.telefon || '')}" type="tel"></div>
      <div class="form-group"><label class="form-label">E-Mail</label>
        <input type="email" class="form-control" id="lf-mail" value="${escH(r.email || '')}"></div>
      <div class="form-group"><label class="form-label">Kundennummer</label>
        <input class="form-control" id="lf-knr" value="${escH(r.kundennummer || '')}"></div>
      <div class="form-group full"><label class="form-label">Notiz</label>
        <textarea class="form-control" id="lf-notiz" rows="2">${escH(r.notiz || '')}</textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  try {
    await api(`/kkh/api/web/lieferanten/${id}`, { method: 'PATCH', body: {
      name: mf(res, 'lf-name')?.trim() || r.name,
      kontakt: mf(res, 'lf-kontakt') || '',
      telefon: mf(res, 'lf-tel') || '',
      email: mf(res, 'lf-mail') || '',
      kundennummer: mf(res, 'lf-knr') || '',
      notiz: mf(res, 'lf-notiz') || '',
    }});
    toast('Lieferant gespeichert');
    reload();
  } catch (e) { toast(e.message, 'err'); }
}

/* ─── Abrechnung ──────────────────────────────────────────────────────── */
async function viewAbrechnung() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Abrechnung & Auswertung', `
      <div id="ab-export"></div>
    `)}
    <div class="filter-bar">
      <div class="filter-group"><label>Von</label>
        <input type="date" class="form-control form-control-sm" id="ab-von" value="${vor30T()}"></div>
      <div class="filter-group"><label>Bis</label>
        <input type="date" class="form-control form-control-sm" id="ab-bis" value="${heute()}"></div>
      <div class="filter-group"><label>Station</label>
        <input type="text" class="form-control form-control-sm" id="ab-sta" placeholder="Alle" style="width:100px"></div>
      <button class="btn btn-primary btn-sm" id="ab-laden">Auswerten</button>
    </div>
    <div class="kpi-grid" id="ab-kpi"></div>
    <div class="section-title">Material-Abrechnung</div>
    <div id="ab-container"><div class="loading">Wird geladen…</div></div>
  `;

  async function lade() {
    const von = document.getElementById('ab-von').value;
    const bis = document.getElementById('ab-bis').value;
    const sta = document.getElementById('ab-sta').value;
    const exportEl = document.getElementById('ab-export');
    if (exportEl) exportEl.innerHTML = `<a href="/kkh/api/web/export/abrechnung?von=${von}&bis=${bis}" target="_blank" class="btn btn-secondary btn-sm">📊 XLSX-Export</a>`;

    const container = document.getElementById('ab-container');
    if (!container) return;
    container.innerHTML = '<div class="loading">Wird geladen…</div>';

    let data;
    try { data = await api(`/kkh/api/web/lager/verbrauch?von=${von}&bis=${bis}&station=${encodeURIComponent(sta)}`); }
    catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    const rows = data?.verbrauch || [];
    const gesamtEk = rows.reduce((s, r) => s + (Number(r.gesamt_ek) || 0), 0);
    const gesamtAnzahl = rows.reduce((s, r) => s + (Number(r.anzahl) || 0), 0);

    const kpiEl = document.getElementById('ab-kpi');
    if (kpiEl) kpiEl.innerHTML = `
      <div class="kpi-card"><div class="kpi-label">Positionen</div><div class="kpi-value">${rows.length}</div><div class="kpi-sub">verschiedene Materialien</div></div>
      <div class="kpi-card info"><div class="kpi-label">Einsätze</div><div class="kpi-value">${gesamtAnzahl}</div><div class="kpi-sub">Gesamteinsätze</div></div>
      <div class="kpi-card ok"><div class="kpi-label">Materialwert EK</div><div class="kpi-value" style="font-size:20px">${fmtEur(gesamtEk)}</div><div class="kpi-sub">Einkaufswert</div></div>
    `;

    new DataGrid(container, {
      data: rows,
      filterKeys: ['station', 'material'],
      columns: [
        { key: 'station', label: 'Station', sort: true, width: '90px',
          render: (v) => v ? badge(v, 'teal') : badge('–', 'gray') },
        { key: 'material', label: 'Material / Arbeit', sort: true },
        { key: 'anzahl', label: 'Anzahl', sort: true, width: '70px', align: 'right' },
        { key: 'ek_preis', label: 'EK-Preis', width: '90px', align: 'right',
          render: (v) => v != null ? fmtEur(v) : '–' },
        { key: 'gesamt_ek', label: 'Gesamt EK', sort: true, width: '110px', align: 'right',
          render: (v) => v != null ? `<strong>${fmtEur(v)}</strong>` : badge('Nicht kalkulierbar', 'gray') },
      ],
    });
  }

  document.getElementById('ab-laden').addEventListener('click', lade);
  await lade();
}

/* ─── Mitarbeiter ─────────────────────────────────────────────────────── */
async function viewMitarbeiter() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Mitarbeiter', `
      <button class="btn btn-primary" id="ma-neu">+ Mitarbeiter</button>
    `)}
    <div id="ma-container"><div class="loading">Wird geladen…</div></div>
  `;

  async function lade() {
    const container = document.getElementById('ma-container');
    if (!container) return;
    let data;
    try { data = await api('/kkh/api/web/mitarbeiter'); }
    catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    new DataGrid(container, {
      data: data?.mitarbeiter || [],
      filterKeys: ['name'],
      columns: [
        { key: 'name', label: 'Name', sort: true, render: (v) => `<strong>${escH(v)}</strong>` },
        { key: 'aktiv', label: 'Status', width: '90px',
          render: (v) => v ? badge('Aktiv', 'ok') : badge('Inaktiv', 'gray') },
        { key: '_actions', label: '', width: '130px', align: 'right',
          render: (v, row) => `<button class="btn btn-xs btn-${row.aktiv ? 'danger' : 'secondary'} ma-toggle" data-name="${escH(row.name)}" data-aktiv="${!row.aktiv}">${row.aktiv ? '⛔ Deaktivieren' : '✅ Aktivieren'}</button>` },
      ],
    });

    container.addEventListener('click', async (e) => {
      const btn = e.target.closest('.ma-toggle');
      if (btn) {
        try {
          await api(`/kkh/api/web/mitarbeiter/${encodeURIComponent(btn.dataset.name)}`, {
            method: 'PATCH', body: { aktiv: btn.dataset.aktiv === 'true' },
          });
          toast('Gespeichert');
          lade();
        } catch (err) { toast(err.message, 'err'); }
      }
    });
  }

  document.getElementById('ma-neu').addEventListener('click', async () => {
    const res = await modal('Mitarbeiter anlegen', `
      <div class="form-group">
        <label class="form-label">Name *</label>
        <input class="form-control" id="ma-name" placeholder="Vor- und Nachname">
      </div>`,
      [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
    if (!res || res.action !== 'ok') return;
    const name = mf(res, 'ma-name')?.trim();
    if (!name) { toast('Name eingeben', 'err'); return; }
    try {
      await api('/kkh/api/web/mitarbeiter', { method: 'POST', body: { name } });
      toast('Mitarbeiter angelegt');
      lade();
    } catch (e) { toast(e.message, 'err'); }
  });

  await lade();
}
