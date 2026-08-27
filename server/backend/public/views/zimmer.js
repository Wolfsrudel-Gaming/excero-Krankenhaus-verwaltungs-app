/**
 * Zimmer & Stationen – KKH TV-Wartung
 * Verbesserungen: Freenet-Badge, inaktiv-Row-Klasse, Sperren-Info,
 *   vollständiges Detail, Bearbeiten-Modal mit neuem modal/mf-API
 */

let zimmerDaten = [];

async function viewZimmer() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Zimmer & Stationen', `
      <a href="/kkh/api/web/export/zimmer" target="_blank" class="btn btn-secondary btn-sm">📊 Excel-Export</a>
      <button class="btn btn-primary" id="z-neu">+ Zimmer</button>
    `)}
    <div class="filter-bar">
      <div class="filter-group"><label>Station</label>
        <select class="form-control form-control-sm" id="z-station">
          <option value="">Alle Stationen</option>
        </select></div>
      <div class="filter-group"><label>Status</label>
        <select class="form-control form-control-sm" id="z-aktiv">
          <option value="">Alle</option>
          <option value="aktiv">Nur aktive</option>
          <option value="inaktiv">Nur inaktive</option>
        </select></div>
      <div class="filter-group"><label>Freenet</label>
        <select class="form-control form-control-sm" id="z-freenet">
          <option value="">Alle</option>
          <option value="abgelaufen">Abgelaufen</option>
          <option value="bald">Läuft bald ab (90 Tage)</option>
          <option value="ok">OK</option>
        </select></div>
    </div>
    <div id="zimmer-container"><div class="loading">Wird geladen…</div></div>
  `;

  let data;
  try { data = await api('/kkh/api/web/rooms'); }
  catch (e) { document.getElementById('zimmer-container').innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

  zimmerDaten = data.rooms || [];

  const stationen = [...new Set(zimmerDaten.map((r) => r.station))].sort();
  const sel = document.getElementById('z-station');
  stationen.forEach((s) => { const o = document.createElement('option'); o.value = s; o.textContent = s; sel.appendChild(o); });

  const heuteDt = heute();
  const in90Dt = new Date(Date.now() + 90 * 86400e3).toISOString().slice(0, 10);

  function filtered() {
    const sta = document.getElementById('z-station').value;
    const akt = document.getElementById('z-aktiv').value;
    const fn = document.getElementById('z-freenet').value;
    return zimmerDaten.filter((r) => {
      if (sta && r.station !== sta) return false;
      if (akt === 'aktiv' && r.inaktiv) return false;
      if (akt === 'inaktiv' && !r.inaktiv) return false;
      if (fn === 'abgelaufen' && !(r.gueltigBis && r.gueltigBis < heuteDt)) return false;
      if (fn === 'bald' && !(r.gueltigBis && r.gueltigBis >= heuteDt && r.gueltigBis <= in90Dt)) return false;
      if (fn === 'ok' && !(r.gueltigBis && r.gueltigBis > in90Dt)) return false;
      return true;
    }).map((r) => ({
      ...r,
      _rowClass: r.inaktiv ? 'inaktiv-row' : (r.gueltigBis && r.gueltigBis < heuteDt ? 'crit-row' : r.gueltigBis && r.gueltigBis <= in90Dt ? 'warn-row' : ''),
    }));
  }

  const container = document.getElementById('zimmer-container');
  let grid;

  function render() {
    const rows = filtered();
    if (!grid) {
      grid = new DataGrid(container, {
        data: rows,
        filterKeys: ['station', 'zimmer', 'tvTyp', 'seriennummer', 'freenetId'],
        columns: [
          { key: 'station', label: 'Station', sort: true, width: '90px',
            render: (v) => badge(v || '–', 'teal') },
          { key: 'zimmer', label: 'Zimmer', sort: true, width: '80px' },
          { key: 'tvTyp', label: 'TV-Typ', sort: true, width: '120px' },
          { key: 'seriennummer', label: 'Seriennummer', sort: true,
            render: (v) => `<code class="mono">${escH(v || '–')}</code>` },
          { key: 'freenetId', label: 'Freenet-ID', sort: true,
            render: (v) => v ? `<code class="mono">${escH(v)}</code>` : '–' },
          { key: 'gueltigBis', label: 'Freenet gültig bis', sort: true, width: '150px',
            render: (v) => freenetBadge(v) },
          { key: 'letztePruefung', label: 'Letzte Prüfung', sort: true, width: '120px',
            render: (v) => v ? fmtDatum(v) : badge('Noch keine', 'gray') },
          { key: 'inaktiv', label: 'Status', width: '80px', align: 'center',
            render: (v) => v ? badge('Inaktiv', 'gray') : badge('Aktiv', 'ok') },
        ],
        onRowClick: (row) => viewZimmerDetail(row.id),
      });
    } else {
      grid.update(rows);
    }
  }

  render();
  ['z-station', 'z-aktiv', 'z-freenet'].forEach((id) =>
    document.getElementById(id)?.addEventListener('change', render));

  // Zimmer-Detail-Request via Navigation (aus Prüfungen-View)
  if (window._zimmerDetailId) {
    const id = window._zimmerDetailId;
    delete window._zimmerDetailId;
    viewZimmerDetail(id);
    return;
  }

  document.getElementById('z-neu').addEventListener('click', () => modalZimmerNeu().then(() => viewZimmer()));
}

async function viewZimmerDetail(id) {
  const el = document.getElementById('content-area');
  el.innerHTML = '<div class="loading">Zimmer wird geladen…</div>';

  let data;
  try { data = await api(`/kkh/api/web/rooms/${encodeURIComponent(id)}`); }
  catch (e) { el.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

  const r = data.room;
  const heuteDt = heute();

  const pdfs = (data.files || []).filter((f) => f.path.endsWith('.pdf'));
  // Signaturen werden serverseitig für PDFs genutzt, aber NICHT im Web angezeigt
  const fotos = (data.files || []).filter((f) => /\.(jpe?g|png)$/i.test(f.path) && !f.path.includes('_signaturen'));
  const sperren = data.sperren || [];

  el.innerHTML = `
    <div class="detail-header">
      <button class="detail-back" id="z-back">← Zurück</button>
      <span class="detail-title">Zimmer ${escH(r.station)} / ${escH(r.zimmer)}</span>
      ${r.inaktiv ? badge('Inaktiv', 'gray') : badge('Aktiv', 'ok')}
      ${r.gueltigBis && r.gueltigBis < heuteDt ? badge('Freenet abgelaufen!', 'err') : ''}
      <div style="margin-left:auto;display:flex;gap:8px;flex-wrap:wrap">
        <button class="btn btn-secondary btn-sm" id="z-edit">✏ Bearbeiten</button>
        <button class="btn btn-danger btn-sm" id="z-toggle">${r.inaktiv ? '✅ Aktivieren' : '⛔ Deaktivieren'}</button>
      </div>
    </div>

    <div class="detail-grid" style="margin-bottom:20px">
      <div class="detail-field"><label>TV-Typ</label><div class="val">${escH(r.tvTyp || '–')}</div></div>
      <div class="detail-field"><label>Seriennummer</label><div class="val mono">${escH(r.seriennummer || '–')}</div></div>
      <div class="detail-field"><label>Freenet-ID</label><div class="val mono">${escH(r.freenetId || '–')}</div></div>
      <div class="detail-field"><label>Freenet gültig bis</label><div class="val">${freenetBadge(r.gueltigBis)}</div></div>
      <div class="detail-field"><label>Zimmer-ID</label><div class="val mono">${escH(r.id)}</div></div>
      <div class="detail-field"><label>Letzte Prüfung</label><div class="val">${r.letztePruefung ? fmtDatum(r.letztePruefung) : badge('Noch keine', 'gray')}</div></div>
    </div>

    ${r.lebenslauf ? `
      <div class="section-title">📝 Lebenslauf / Bemerkungen</div>
      <div class="lebenslauf" style="margin-bottom:20px">${escH(r.lebenslauf)}</div>` : ''}

    ${sperren.length > 0 ? `
      <div class="section-title">🔒 Aktive Sperren (${sperren.length})</div>
      <div class="dg-wrap" style="margin-bottom:20px">
        <table class="data-table">
          <thead><tr><th>Gesetzt am</th><th>Kommentar</th></tr></thead>
          <tbody>${sperren.map((s) => `<tr><td>${fmtDatum(s.created_at || s.datum)}</td><td>${escH(s.kommentar || '–')}</td></tr>`).join('')}</tbody>
        </table>
      </div>` : ''}

    ${pdfs.length > 0 ? `
      <div class="section-title">📄 Prüfbericht-PDFs (${pdfs.length})</div>
      <div class="chip-row" style="margin-bottom:20px">
        ${pdfs.map((f) => `<a class="pdf-link" href="/kkh/api/web/file?path=${encodeURIComponent(f.path)}" target="_blank">📄 ${escH(f.path.split('/').pop())}</a>`).join('')}
      </div>` : ''}

    <div class="section-title">✅ Prüfhistorie (${(data.inspections || []).length})</div>
    <div id="z-insp-container" style="margin-bottom:20px"><div class="loading" style="height:80px"></div></div>

    ${fotos.length > 0 ? `
      <div class="section-title">📷 Fotos (${fotos.length})</div>
      <div class="foto-grid" style="margin-bottom:20px">
        ${fotos.map((f) => `
          <div class="foto-thumb">
            <a href="/kkh/api/web/file?path=${encodeURIComponent(f.path)}" target="_blank">
              <img src="/kkh/api/web/thumb?path=${encodeURIComponent(f.path)}" alt="${escH(f.path.split('/').pop())}" loading="lazy">
            </a>
            <div class="foto-name">${escH(f.path.split('/').pop())}</div>
          </div>`).join('')}
      </div>` : ''}
  `;

  // Prüfhistorie
  const inspContainer = document.getElementById('z-insp-container');
  if (inspContainer) {
    new DataGrid(inspContainer, {
      data: (data.inspections || []).map((i) => ({
        ...i,
        _arbeiten: (i.daten?.arbeiten || []).join(', ') || '–',
        _bemerkungen: i.daten?.bemerkungen || '',
      })),
      filterKeys: ['mitarbeiter', '_arbeiten'],
      columns: [
        { key: 'datum', label: 'Datum', sort: true, width: '100px',
          render: (v) => `<span class="mono">${fmtDatum(v)}</span>` },
        { key: 'mitarbeiter', label: 'Prüfer', sort: true, width: '120px',
          render: (v, row) => escH(v || row.daten?.mitarbeiter || '–') },
        { key: '_arbeiten', label: 'Durchgeführte Arbeiten',
          render: (v) => v.split(', ').map((a) => a !== '–' ? `<span class="chip">${escH(a)}</span>` : '').join(' ') || '–' },
        { key: '_bemerkungen', label: 'Bemerkungen',
          render: (v) => `<span style="font-size:12px;color:var(--muted)">${escH(v)}</span>` },
      ],
    });
  }

  // Events
  document.getElementById('z-back').addEventListener('click', () => viewZimmer());

  document.getElementById('z-edit').addEventListener('click', () => modalZimmerBearbeiten(r, id));

  document.getElementById('z-toggle').addEventListener('click', async () => {
    const neuStatus = !r.inaktiv;
    if (!(await confirm(`Zimmer ${r.id} ${neuStatus ? 'deaktivieren' : 'aktivieren'}?`))) return;
    try {
      await api(`/kkh/api/web/rooms/${encodeURIComponent(id)}`, { method: 'PATCH', body: { inaktiv: neuStatus } });
      toast(`Zimmer ${neuStatus ? 'deaktiviert' : 'aktiviert'}`);
      viewZimmerDetail(id);
    } catch (e) { toast(e.message, 'err'); }
  });
}

async function modalZimmerNeu() {
  const res = await modal('Neues Zimmer anlegen', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Station *</label>
        <input class="form-control" id="m-station" placeholder="z.B. A4"></div>
      <div class="form-group"><label class="form-label">Zimmer *</label>
        <input class="form-control" id="m-zimmer" placeholder="z.B. 01a"></div>
      <div class="form-group"><label class="form-label">TV-Typ</label>
        <input class="form-control" id="m-tv" placeholder="z.B. Samsung 43 Zoll"></div>
      <div class="form-group"><label class="form-label">Seriennummer</label>
        <input class="form-control" id="m-sn"></div>
      <div class="form-group"><label class="form-label">Freenet-ID</label>
        <input class="form-control" id="m-fn"></div>
      <div class="form-group"><label class="form-label">Freenet gültig bis</label>
        <input type="date" class="form-control" id="m-gb"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  const station = mf(res, 'm-station')?.trim();
  const zimmer = mf(res, 'm-zimmer')?.trim();
  if (!station || !zimmer) { toast('Station und Zimmer sind Pflichtfelder', 'err'); return; }
  try {
    await api('/kkh/api/web/rooms', { method: 'POST', body: {
      station, zimmer,
      tvTyp: mf(res, 'm-tv') || '',
      seriennummer: mf(res, 'm-sn') || '',
      freenetId: mf(res, 'm-fn') || '',
      gueltigBis: mf(res, 'm-gb') || '',
    }});
    toast('Zimmer erfolgreich angelegt');
  } catch (e) { toast(e.message, 'err'); }
}

async function modalZimmerBearbeiten(r, id) {
  const res = await modal(`Zimmer ${escH(r.id)} bearbeiten`, `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">TV-Typ</label>
        <input class="form-control" id="m-tv" value="${escH(r.tvTyp || '')}"></div>
      <div class="form-group"><label class="form-label">Seriennummer</label>
        <input class="form-control" id="m-sn" value="${escH(r.seriennummer || '')}"></div>
      <div class="form-group"><label class="form-label">Freenet-ID</label>
        <input class="form-control" id="m-fn" value="${escH(r.freenetId || '')}"></div>
      <div class="form-group"><label class="form-label">Freenet gültig bis</label>
        <input type="date" class="form-control" id="m-gb" value="${escH(r.gueltigBis || '')}"></div>
      <div class="form-group full"><label class="form-label">Lebenslauf / Bemerkungen</label>
        <textarea class="form-control" id="m-lv" rows="5">${escH(r.lebenslauf || '')}</textarea></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  try {
    await api(`/kkh/api/web/rooms/${encodeURIComponent(id)}`, { method: 'PATCH', body: {
      tvTyp: mf(res, 'm-tv') || '',
      seriennummer: mf(res, 'm-sn') || '',
      freenetId: mf(res, 'm-fn') || '',
      gueltigBis: mf(res, 'm-gb') || '',
      lebenslauf: mf(res, 'm-lv') || '',
    }});
    toast('Zimmer gespeichert');
    viewZimmerDetail(id);
  } catch (e) { toast(e.message, 'err'); }
}
