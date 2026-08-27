/**
 * Stundenzettel – KKH TV-Wartung
 * Fixes: next-nr Feld, Team-Edit Vorbelegung, Prüfungen im Detail, neues DataGrid-API
 */

async function viewStundenzettel() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Stundenzettel', `
      <a href="/kkh/api/web/export/stundenzettel" target="_blank" class="btn btn-secondary btn-sm">📊 Excel-Export</a>
      <button class="btn btn-primary" id="sz-neu">+ Neu</button>
    `)}
    <div class="filter-bar">
      <div class="filter-group"><label>Von</label>
        <input type="date" class="form-control form-control-sm" id="sz-von" value="${monatVon()}"></div>
      <div class="filter-group"><label>Bis</label>
        <input type="date" class="form-control form-control-sm" id="sz-bis" value="${heute()}"></div>
      <div class="filter-group"><label>Station</label>
        <input type="text" class="form-control form-control-sm" id="sz-sta-filter" placeholder="Alle" style="width:100px"></div>
      <button class="btn btn-primary btn-sm" id="sz-laden">Laden</button>
      <button class="btn btn-ghost btn-sm" id="sz-alle">Alle laden</button>
    </div>
    <div id="sz-container"><div class="loading">Wird geladen…</div></div>
  `;

  let alleZettel = [];

  async function laden(alle = false) {
    const container = document.getElementById('sz-container');
    if (!container) return;
    container.innerHTML = '<div class="loading">Wird geladen…</div>';
    try {
      const d = await api('/kkh/api/web/stundenzettel');
      alleZettel = d?.zettel || [];
    } catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    let rows = [...alleZettel];
    if (!alle) {
      const von = document.getElementById('sz-von')?.value;
      const bis = document.getElementById('sz-bis')?.value;
      const sf = document.getElementById('sz-sta-filter')?.value?.trim().toLowerCase();
      if (von) rows = rows.filter((z) => z.zeitraum_start >= von);
      if (bis) rows = rows.filter((z) => z.zeitraum_start <= bis);
      if (sf) rows = rows.filter((z) => (z.station || '').toLowerCase().includes(sf));
    }

    new DataGrid(container, {
      data: rows,
      filterKeys: ['station', 'techniker', 'auftragsnummer'],
      columns: [
        { key: 'zeitraum_start', label: 'Zeitraum ab', sort: true, width: '110px',
          render: (v) => `<span class="mono">${fmtDatum(v)}</span>` },
        { key: 'station', label: 'Station', sort: true, width: '90px',
          render: (v) => badge(v || '–', 'teal') },
        { key: 'auftragsnummer', label: 'Auftragsnr.', sort: true, width: '130px',
          render: (v) => `<code class="mono">${escH(v || '–')}</code>` },
        { key: 'techniker', label: 'Techniker', sort: true },
        { key: 'datum', label: 'Datum', sort: true, width: '100px',
          render: (v) => fmtDatum(v) },
        { key: 'stunden', label: 'Std.', sort: true, width: '70px', align: 'right' },
        { key: 'anfahrt', label: 'Anfahrt', sort: true, width: '70px', align: 'right' },
        { key: '_teamAnz', label: 'Team', width: '60px', align: 'center',
          render: (v, row) => `<span class="badge badge-info">${row.team?.anzahl ?? 0}</span>` },
        { key: '_pdf', label: '', width: '50px', align: 'center',
          render: (v, row) =>
            `<a class="btn btn-xs btn-ghost" href="/kkh/api/web/stundenzettel/pdf?station=${encodeURIComponent(row.station)}&zeitraum=${encodeURIComponent(row.zeitraum_start)}" target="_blank" title="PDF öffnen">📄</a>` },
      ],
      onRowClick: (row) => viewStundenzettelDetail(row.station, row.zeitraum_start),
    });
  }

  document.getElementById('sz-laden').addEventListener('click', () => laden(false));
  document.getElementById('sz-alle').addEventListener('click', () => laden(true));
  document.getElementById('sz-neu').addEventListener('click', () => modalStundenzettelNeu().then(() => laden(false)));

  await laden(false);
}

async function viewStundenzettelDetail(station, zeitraumStart) {
  const el = document.getElementById('content-area');
  el.innerHTML = '<div class="loading">Stundenzettel wird geladen…</div>';

  let data;
  try { data = await api(`/kkh/api/web/stundenzettel/detail?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraumStart)}`); }
  catch (e) { el.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

  const z = data.zettel;
  const eintraege = data.eintraege || [];
  const inspections = data.inspections || [];
  const pdfUrl = `/kkh/api/web/stundenzettel/pdf?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraumStart)}`;

  el.innerHTML = `
    <div class="detail-header">
      <button class="detail-back" id="sz-back">← Zurück</button>
      <span class="detail-title">${escH(z.station)} · ${fmtDatum(z.zeitraum_start)}</span>
      ${z.auftragsnummer ? `<span class="badge badge-teal">${escH(z.auftragsnummer)}</span>` : ''}
      <div style="margin-left:auto;display:flex;gap:8px;flex-wrap:wrap">
        <a class="btn btn-secondary btn-sm" href="${pdfUrl}" target="_blank">📄 PDF öffnen</a>
        <button class="btn btn-primary btn-sm" id="sz-save">💾 Speichern</button>
        <button class="btn btn-danger btn-sm" id="sz-del">🗑 Löschen</button>
      </div>
    </div>

    <div class="settings-section" style="margin-bottom:16px">
      <h3>Kopfdaten</h3>
      <div class="form-grid">
        <div class="form-group"><label class="form-label">Auftragsnummer</label>
          <input class="form-control" id="sz-nr" value="${escH(z.auftragsnummer || '')}"></div>
        <div class="form-group"><label class="form-label">Station</label>
          <input class="form-control" id="sz-sta" value="${escH(z.station || '')}"></div>
        <div class="form-group"><label class="form-label">Zeitraum ab</label>
          <input type="date" class="form-control" id="sz-zr" value="${escH(z.zeitraum_start || '')}"></div>
        <div class="form-group"><label class="form-label">Datum</label>
          <input type="date" class="form-control" id="sz-dat" value="${escH(z.datum || '')}"></div>
        <div class="form-group"><label class="form-label">Stunden (Kopf)</label>
          <input class="form-control" id="sz-std" value="${escH(String(z.stunden || ''))}"></div>
        <div class="form-group"><label class="form-label">Anfahrt (Kopf)</label>
          <input class="form-control" id="sz-anf" value="${escH(String(z.anfahrt || ''))}"></div>
        <div class="form-group full"><label class="form-label">Techniker</label>
          <input class="form-control" id="sz-tech" value="${escH(z.techniker || '')}"></div>
      </div>
    </div>

    <div class="settings-section" style="margin-bottom:16px">
      <h3 style="display:flex;align-items:center;gap:10px">Team-Einträge
        <button class="btn btn-secondary btn-sm" id="sze-add" style="margin-left:auto">+ Eintrag</button>
      </h3>
      <div id="sze-container"></div>
    </div>

    ${inspections.length > 0 ? `
    <div class="settings-section" style="margin-bottom:16px">
      <h3>Prüfungen in diesem Zeitraum (${inspections.length})</h3>
      <div id="sz-inspections-container"></div>
    </div>` : ''}
  `;

  // Zurück
  document.getElementById('sz-back').addEventListener('click', () => viewStundenzettel());

  // Speichern
  document.getElementById('sz-save').addEventListener('click', async () => {
    try {
      await api('/kkh/api/web/stundenzettel', { method: 'PUT', body: {
        station: document.getElementById('sz-sta').value,
        zeitraumStart: document.getElementById('sz-zr').value,
        auftragsnummer: document.getElementById('sz-nr').value,
        datum: document.getElementById('sz-dat').value,
        stunden: document.getElementById('sz-std').value,
        anfahrt: document.getElementById('sz-anf').value,
        techniker: document.getElementById('sz-tech').value,
      }});
      toast('Stundenzettel gespeichert');
    } catch (e) { toast(e.message, 'err'); }
  });

  // Löschen
  document.getElementById('sz-del').addEventListener('click', async () => {
    if (!(await confirm(`Stundenzettel ${station} / ${fmtDatum(zeitraumStart)} wirklich löschen?`))) return;
    try {
      await api(`/kkh/api/web/stundenzettel?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraumStart)}`, { method: 'DELETE' });
      toast('Stundenzettel gelöscht');
      viewStundenzettel();
    } catch (e) { toast(e.message, 'err'); }
  });

  // Team-Einträge
  function renderEintraege(rows) {
    const container = document.getElementById('sze-container');
    if (!container) return;
    if (rows.length === 0) {
      container.innerHTML = '<p style="color:var(--muted);font-style:italic;padding:8px 0">Noch keine Team-Einträge vorhanden</p>';
      return;
    }
    new DataGrid(container, {
      data: rows,
      filterKeys: ['mitarbeiter'],
      columns: [
        { key: 'mitarbeiter', label: 'Mitarbeiter', sort: true },
        { key: 'stunden', label: 'Stunden', sort: true, width: '90px', align: 'right' },
        { key: 'anfahrt', label: 'Anfahrt', sort: true, width: '90px', align: 'right' },
        { key: '_actions', label: '', width: '130px', align: 'right',
          render: (v, row) => `
            <button class="btn btn-xs btn-secondary sze-edit" data-ma="${escH(row.mitarbeiter)}" data-std="${escH(String(row.stunden || ''))}" data-anf="${escH(String(row.anfahrt || ''))}">Bearb.</button>
            <button class="btn btn-xs btn-danger sze-del" data-ma="${escH(row.mitarbeiter)}">Löschen</button>
          ` },
      ],
    });
  }
  renderEintraege(eintraege);

  // Delegiertes Klick-Handling für Team-Tabelle
  document.getElementById('sze-container').addEventListener('click', async (e) => {
    const editBtn = e.target.closest('.sze-edit');
    const delBtn = e.target.closest('.sze-del');
    if (editBtn) {
      const ma = editBtn.dataset.ma;
      const std = editBtn.dataset.std;
      const anf = editBtn.dataset.anf;
      await bearbeiteZettelEintragModal(station, zeitraumStart, ma, std, anf);
    } else if (delBtn) {
      const ma = delBtn.dataset.ma;
      if (!(await confirm(`Eintrag von ${ma} löschen?`))) return;
      try {
        await api(`/kkh/api/web/zettel-eintraege?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraumStart)}&mitarbeiter=${encodeURIComponent(ma)}`, { method: 'DELETE' });
        toast('Eintrag gelöscht');
        viewStundenzettelDetail(station, zeitraumStart);
      } catch (err) { toast(err.message, 'err'); }
    }
  });

  // + Eintrag
  document.getElementById('sze-add').addEventListener('click', async () => {
    await bearbeiteZettelEintragModal(station, zeitraumStart, '', '', '');
  });

  // Prüfungen
  const inspCont = document.getElementById('sz-inspections-container');
  if (inspCont && inspections.length > 0) {
    new DataGrid(inspCont, {
      data: inspections,
      filterKeys: ['station', 'zimmer', 'mitarbeiter'],
      columns: [
        { key: 'datum', label: 'Datum', sort: true, width: '100px', render: (v) => fmtDatum(v) },
        { key: 'station', label: 'Station', sort: true, width: '80px' },
        { key: 'zimmer', label: 'Zimmer', sort: true, width: '80px' },
        { key: 'mitarbeiter', label: 'Prüfer', sort: true },
      ],
    });
  }
}

async function bearbeiteZettelEintragModal(station, zeitraumStart, mitarbeiter, stunden, anfahrt) {
  const istNeu = !mitarbeiter;
  const res = await modal(istNeu ? 'Team-Eintrag hinzufügen' : `Eintrag: ${mitarbeiter}`, `
    <div class="form-grid">
      ${istNeu ? `
        <div class="form-group full"><label class="form-label">Mitarbeiter *</label>
          <input class="form-control" id="ze-ma" value="${escH(mitarbeiter)}"></div>` : ''}
      <div class="form-group"><label class="form-label">Stunden</label>
        <input class="form-control" id="ze-std" value="${escH(stunden)}" placeholder="z.B. 8,5"></div>
      <div class="form-group"><label class="form-label">Anfahrt (km)</label>
        <input class="form-control" id="ze-anf" value="${escH(anfahrt)}" placeholder="z.B. 42"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  const ma = istNeu ? mf(res, 'ze-ma') : mitarbeiter;
  if (!ma) { toast('Mitarbeitername eingeben', 'err'); return; }
  try {
    await api('/kkh/api/web/zettel-eintraege', { method: 'PUT', body: {
      station, zeitraumStart,
      mitarbeiter: ma,
      stunden: mf(res, 'ze-std') || '',
      anfahrt: mf(res, 'ze-anf') || '',
    }});
    toast('Eintrag gespeichert');
    viewStundenzettelDetail(station, zeitraumStart);
  } catch (e) { toast(e.message, 'err'); }
}

async function modalStundenzettelNeu() {
  let nr = '';
  try {
    const nrData = await api('/kkh/api/web/stundenzettel/next-nr');
    nr = nrData?.auftragsnummer || nrData?.nr || '';
  } catch {}

  const res = await modal('Neuer Stundenzettel', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Auftragsnummer</label>
        <input class="form-control" id="m-nr" value="${escH(nr)}" placeholder="Wird automatisch vergeben"></div>
      <div class="form-group"><label class="form-label">Station *</label>
        <input class="form-control" id="m-sta" placeholder="z.B. KKH-3B"></div>
      <div class="form-group"><label class="form-label">Zeitraum ab *</label>
        <input type="date" class="form-control" id="m-zr" value="${heute()}"></div>
      <div class="form-group"><label class="form-label">Techniker</label>
        <input class="form-control" id="m-tech" placeholder="Name des Technikers"></div>
      <div class="form-group"><label class="form-label">Datum Einsatz</label>
        <input type="date" class="form-control" id="m-dat" value="${heute()}"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  const sta = mf(res, 'm-sta');
  const zr = mf(res, 'm-zr');
  if (!sta || !zr) { toast('Station und Zeitraum sind Pflichtfelder', 'err'); return; }
  try {
    await api('/kkh/api/web/stundenzettel', { method: 'PUT', body: {
      auftragsnummer: mf(res, 'm-nr') || nr,
      station: sta,
      zeitraumStart: zr,
      datum: mf(res, 'm-dat') || '',
      techniker: mf(res, 'm-tech') || '',
      stunden: '', anfahrt: '',
    }});
    toast('Stundenzettel angelegt');
    await viewStundenzettelDetail(sta, zr);
  } catch (e) { toast(e.message, 'err'); }
}
