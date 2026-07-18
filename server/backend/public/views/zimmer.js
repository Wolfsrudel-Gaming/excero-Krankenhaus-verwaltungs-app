/* ── Zimmer-Views ───────────────────────────────────────────────────── */
let zimmerGrid = null;
let zimmerDaten = [];

async function viewZimmer() {
  const area = document.getElementById('content-area');
  area.innerHTML = `
    <div class="page-header">
      <span class="page-title">Zimmer &amp; Stationen</span>
      <span class="page-sub" id="zimmer-count"></span>
    </div>
    <div class="toolbar">
      <input type="search" id="z-suche" placeholder="Suche Station / Zimmer / Seriennummer…">
      <select id="z-station">
        <option value="">Alle Stationen</option>
      </select>
      <select id="z-aktiv">
        <option value="">Alle</option>
        <option value="aktiv">Nur aktive</option>
        <option value="inaktiv">Nur inaktive</option>
      </select>
      <span class="spacer"></span>
      ${exportBtn('zimmer')}
      <button class="btn btn-primary" id="z-neu">+ Zimmer</button>
    </div>
    <div class="grid-wrap"><div id="zimmer-grid"></div></div>`;

  const data = await api('/kkh/api/web/rooms');
  if (!data) return;
  zimmerDaten = data.rooms || [];

  const stationen = [...new Set(zimmerDaten.map((r) => r.station))].sort();
  const sel = document.getElementById('z-station');
  stationen.forEach((s) => { const o = document.createElement('option'); o.value = s; o.textContent = s; sel.appendChild(o); });

  function filtered() {
    const q = document.getElementById('z-suche').value.toLowerCase();
    const sta = document.getElementById('z-station').value;
    const akt = document.getElementById('z-aktiv').value;
    return zimmerDaten.filter((r) => {
      if (sta && r.station !== sta) return false;
      if (akt === 'aktiv' && r.inaktiv) return false;
      if (akt === 'inaktiv' && !r.inaktiv) return false;
      if (q && ![r.station, r.zimmer, r.seriennummer, r.freenetId, r.tvTyp].some((v) => String(v || '').toLowerCase().includes(q))) return false;
      return true;
    });
  }

  const heute = new Date().toISOString().slice(0, 10);
  const in90 = new Date(Date.now() + 90 * 86400e3).toISOString().slice(0, 10);

  zimmerGrid = new DataGrid('zimmer-grid', {
    columns: [
      { key: 'station', label: 'Station' },
      { key: 'zimmer', label: 'Zimmer' },
      { key: 'tvTyp', label: 'TV-Typ' },
      { key: 'seriennummer', label: 'Seriennummer', cls: 'mono' },
      { key: 'freenetId', label: 'Freenet-ID', cls: 'mono' },
      { key: 'gueltigBis', label: 'Gültig bis', render: (r) => {
        if (!r.gueltigBis) return '–';
        const cls = r.gueltigBis < heute ? 'badge-err' : r.gueltigBis < in90 ? 'badge-warn' : 'badge-ok';
        return `<span class="badge ${cls}">${escH(r.gueltigBis)}</span>`;
      }},
      { key: 'letztePruefung', label: 'Letzte Prüfung' },
      { key: 'inaktiv', label: 'Status', render: (r) =>
        r.inaktiv ? '<span class="badge badge-muted">Inaktiv</span>' : '<span class="badge badge-ok">Aktiv</span>' },
    ],
    rows: filtered(),
    rowClass: (r) => r.inaktiv ? 'inaktiv-row' : '',
    rowClick: (r) => viewZimmerDetail(r.id),
    actions: (r) => `<button class="btn btn-secondary btn-sm" onclick="viewZimmerDetail('${escH(r.id)}')">Detail</button>`,
  });

  const aktualisiere = () => {
    const rows = filtered();
    document.getElementById('zimmer-count').textContent = `${rows.length} von ${zimmerDaten.length}`;
    zimmerGrid.update(rows);
  };
  aktualisiere();
  ['z-suche', 'z-station', 'z-aktiv'].forEach((id) => document.getElementById(id)?.addEventListener('input', aktualisiere));

  document.getElementById('z-neu').addEventListener('click', () => modalZimmerNeu());
}

async function viewZimmerDetail(id) {
  const area = document.getElementById('content-area');
  area.innerHTML = '<div class="page-header"><span class="page-title">Zimmer wird geladen…</span></div>';
  const data = await api(`/kkh/api/web/rooms/${encodeURIComponent(id)}`);
  if (!data) return;
  const r = data.room;
  const heute = new Date().toISOString().slice(0, 10);

  let pdfLinks = '';
  (data.files || []).filter((f) => f.path.endsWith('.pdf')).forEach((f) => {
    pdfLinks += `<a class="pdf-link" href="/kkh/api/web/file?path=${encodeURIComponent(f.path)}" target="_blank">📄 ${escH(f.path.split('/').pop())}</a>`;
  });

  let fotoHtml = '';
  const fotos = (data.files || []).filter((f) => /\.(jpe?g|png)$/i.test(f.path));
  if (fotos.length) {
    fotoHtml = `<div class="card"><div class="card-title">📷 Fotos (${fotos.length})</div>
      <div class="foto-grid">${fotos.map((f) =>
        `<a href="/kkh/api/web/file?path=${encodeURIComponent(f.path)}" target="_blank">
          <img src="/kkh/api/web/thumb?path=${encodeURIComponent(f.path)}" alt="Foto" loading="lazy"></a>`
      ).join('')}</div></div>`;
  }

  area.innerHTML = `
    <a class="detail-back" href="#" id="z-back">← Zurück zu Zimmer</a>
    <div class="page-header">
      <span class="page-title">${escH(r.station)} · ${escH(r.zimmer)}</span>
      <span class="page-sub">${escH(r.id)}</span>
      ${r.inaktiv ? '<span class="badge badge-muted">Inaktiv</span>' : '<span class="badge badge-ok">Aktiv</span>'}
    </div>

    <div class="card">
      <div class="card-title">Stammdaten <span style="margin-left:auto"><button class="btn btn-secondary btn-sm" id="z-edit">Bearbeiten</button></span></div>
      <div class="detail-grid">
        <div class="detail-field"><label>TV-Typ</label><span class="val">${fmt(r.tvTyp)}</span></div>
        <div class="detail-field"><label>Seriennummer</label><span class="val mono">${fmt(r.seriennummer)}</span></div>
        <div class="detail-field"><label>Freenet-ID</label><span class="val mono">${fmt(r.freenetId)}</span></div>
        <div class="detail-field"><label>Gültig bis</label><span class="val">
          ${r.gueltigBis ? `<span class="badge ${r.gueltigBis < heute ? 'badge-err' : 'badge-ok'}">${escH(r.gueltigBis)}</span>` : '–'}
        </span></div>
        <div class="detail-field"><label>Letzte Prüfung</label><span class="val">${fmt(r.letztePruefung)}</span></div>
      </div>
      ${r.lebenslauf ? `<div style="margin-top:12px"><div class="form-label">Lebenslauf / Bemerkungen</div><div class="lebenslauf">${escH(r.lebenslauf)}</div></div>` : ''}
    </div>

    ${pdfLinks ? `<div class="card"><div class="card-title">📄 Prüfbericht-PDFs</div><div class="chip-row">${pdfLinks}</div></div>` : ''}

    <div class="card">
      <div class="card-title">Prüfhistorie (${data.inspections.length})</div>
      <div class="grid-wrap"><div id="z-insp"></div></div>
    </div>
    ${fotoHtml}`;

  document.getElementById('z-back').addEventListener('click', (e) => { e.preventDefault(); viewZimmer(); });
  document.getElementById('z-edit').addEventListener('click', () => modalZimmerBearbeiten(r, data, id));

  new DataGrid('z-insp', {
    columns: [
      { key: 'datum', label: 'Datum', cls: 'mono' },
      { key: '_arb', label: 'Arbeiten', render: (insp) => {
        return (insp.daten?.arbeiten || []).map((a) => `<span class="chip">${escH(a)}</span>`).join(' ') || '–';
      }},
      { key: '_bem', label: 'Bemerkungen', render: (insp) => fmt(insp.daten?.bemerkungen) },
    ],
    rows: data.inspections.map((i) => ({ ...i, _key: i.uuid })),
  });
}

async function modalZimmerNeu() {
  const res = await modal('Neues Zimmer anlegen', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Station <span class="req">*</span></label>
        <input class="form-control" id="m-station" placeholder="z. B. A4"></div>
      <div class="form-group"><label class="form-label">Zimmer <span class="req">*</span></label>
        <input class="form-control" id="m-zimmer" placeholder="z. B. 01a"></div>
      <div class="form-group"><label class="form-label">TV-Typ</label>
        <input class="form-control" id="m-tv"></div>
      <div class="form-group"><label class="form-label">Seriennummer</label>
        <input class="form-control" id="m-sn"></div>
      <div class="form-group"><label class="form-label">Freenet-ID</label>
        <input class="form-control" id="m-fn"></div>
      <div class="form-group"><label class="form-label">Gültig bis</label>
        <input type="date" class="form-control" id="m-gb"></div>
    </div>
    <div id="m-err" class="form-err" style="margin-top:8px;"></div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  const station = document.getElementById('m-station')?.value.trim();
  const zimmer = document.getElementById('m-zimmer')?.value.trim();
  if (!station || !zimmer) { toast('Station und Zimmer sind Pflicht', 'err'); return; }
  try {
    await api('/kkh/api/web/rooms', { method: 'POST', body: {
      station, zimmer,
      tvTyp: document.getElementById('m-tv')?.value || '',
      seriennummer: document.getElementById('m-sn')?.value || '',
      freenetId: document.getElementById('m-fn')?.value || '',
      gueltigBis: document.getElementById('m-gb')?.value || '',
    }});
    toast('Zimmer angelegt');
    viewZimmer();
  } catch (e) { toast(e.message, 'err'); }
}

async function modalZimmerBearbeiten(r, data, id) {
  const res = await modal(`Zimmer ${r.id} bearbeiten`, `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">TV-Typ</label>
        <input class="form-control" id="m-tv" value="${escH(r.tvTyp)}"></div>
      <div class="form-group"><label class="form-label">Seriennummer</label>
        <input class="form-control" id="m-sn" value="${escH(r.seriennummer)}"></div>
      <div class="form-group"><label class="form-label">Freenet-ID</label>
        <input class="form-control" id="m-fn" value="${escH(r.freenetId)}"></div>
      <div class="form-group"><label class="form-label">Gültig bis</label>
        <input type="date" class="form-control" id="m-gb" value="${escH(r.gueltigBis)}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Lebenslauf / Bemerkungen</label>
        <textarea class="form-control" id="m-lv" rows="5">${escH(r.lebenslauf)}</textarea></div>
      <div class="form-group"><label class="form-label">Status</label>
        <select class="form-control" id="m-akt">
          <option value="aktiv" ${!r.inaktiv ? 'selected' : ''}>Aktiv</option>
          <option value="inaktiv" ${r.inaktiv ? 'selected' : ''}>Inaktiv</option>
        </select></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    await api(`/kkh/api/web/rooms/${encodeURIComponent(id)}`, { method: 'PATCH', body: {
      tvTyp: document.getElementById('m-tv')?.value || '',
      seriennummer: document.getElementById('m-sn')?.value || '',
      freenetId: document.getElementById('m-fn')?.value || '',
      gueltigBis: document.getElementById('m-gb')?.value || '',
      lebenslauf: document.getElementById('m-lv')?.value || '',
      inaktiv: document.getElementById('m-akt')?.value === 'inaktiv',
    }});
    toast('Zimmer gespeichert');
    viewZimmerDetail(id);
  } catch (e) { toast(e.message, 'err'); }
}
