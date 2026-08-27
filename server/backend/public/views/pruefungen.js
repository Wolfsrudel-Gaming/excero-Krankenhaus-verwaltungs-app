/**
 * Prüfungen – KKH TV-Wartung
 * - Backend-Filter (von/bis, Mitarbeiter, Station)
 * - Prüfer-Spalte (mitarbeiter-Feld)
 * - Detail-Dialog mit Prüfpunkten
 * - Link zum Zimmer
 */

async function viewPruefungen() {
  const el = document.getElementById('content-area');
  let pfRows = [];

  el.innerHTML = `
    ${pageHeader('Prüfungen', `
      <a href="/kkh/api/web/export/pruefungen.xlsx" target="_blank" class="btn btn-secondary btn-sm">📊 Excel-Export</a>
    `)}
    <div class="filter-bar">
      <div class="filter-group"><label>Von</label>
        <input type="date" class="form-control form-control-sm" id="pf-von" value="${monatVon()}"></div>
      <div class="filter-group"><label>Bis</label>
        <input type="date" class="form-control form-control-sm" id="pf-bis" value="${heute()}"></div>
      <div class="filter-group"><label>Station</label>
        <input type="text" class="form-control form-control-sm" id="pf-station" placeholder="Alle" style="width:100px"></div>
      <div class="filter-group"><label>Prüfer</label>
        <input type="text" class="form-control form-control-sm" id="pf-ma" placeholder="Alle" style="width:120px"></div>
      <button class="btn btn-primary btn-sm" id="pf-laden">Laden</button>
      <button class="btn btn-ghost btn-sm" id="pf-alle">Alle laden</button>
    </div>
    <div id="pf-container"><div class="loading">Wird geladen…</div></div>
  `;

  async function laden(limitAlles = false) {
    const von = document.getElementById('pf-von').value;
    const bis = document.getElementById('pf-bis').value;
    const station = document.getElementById('pf-station').value.trim();
    const ma = document.getElementById('pf-ma').value.trim();
    const params = new URLSearchParams({ limit: limitAlles ? 2000 : 500 });
    if (!limitAlles) {
      if (von) params.set('von', von);
      if (bis) params.set('bis', bis);
    }
    if (station) params.set('station', station);
    if (ma) params.set('mitarbeiter', ma);

    const container = document.getElementById('pf-container');
    if (!container) return;
    container.innerHTML = '<div class="loading">Wird geladen…</div>';

    let data;
    try { data = await api(`/kkh/api/web/inspections?${params}`); }
    catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    const rows = (data.inspections || []).map((p) => {
      const d = p.daten || {};
      const arbeiten = (d.arbeiten || []).join(', ') || '–';
      const material = (d.material || []).join(', ') || '–';
      return { ...p, _arbeiten: arbeiten, _material: material };
    });
    pfRows = rows;

    const grid = new DataGrid(container, {
      data: rows,
      filterKeys: ['station', 'zimmer', 'mitarbeiter', '_arbeiten', '_material'],
      columns: [
        { key: 'datum', label: 'Datum', sort: true, width: '100px',
          render: (v) => `<span class="mono">${fmtDatum(v)}</span>` },
        { key: 'station', label: 'Station', sort: true, width: '90px',
          render: (v) => badge(v || '–', 'teal') },
        { key: 'zimmer', label: 'Zimmer', sort: true, width: '80px' },
        { key: 'tv_typ', label: 'TV-Typ', sort: true, width: '100px' },
        { key: 'mitarbeiter', label: 'Prüfer', sort: true, width: '120px',
          render: (v, row) => escH(v || row.daten?.mitarbeiter || '–') },
        { key: '_arbeiten', label: 'Durchgeführte Arbeiten',
          render: (v) => `<span style="max-width:260px;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis" title="${escH(v)}">${escH(v)}</span>` },
        { key: '_material', label: 'Materialverbrauch',
          render: (v) => `<span style="max-width:180px;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis" title="${escH(v)}">${escH(v)}</span>` },
        { key: 'uuid', label: '', width: '60px', align: 'center',
          render: (v, row) => `<button class="btn btn-xs btn-ghost" data-detail-uuid="${escH(v)}">Details</button>` },
      ],
      onRowClick: (row) => zeigeDetail(row),
    });
  }

  async function zeigeDetail(p) {
    if (!p) return;
    const d = p.daten || {};
    const punkte = d.punkte || [];
    const arbeiten = d.arbeiten || [];
    const material = d.material || [];
    const anmerkungen = d.anmerkungen || '';

    const pktHtml = punkte.length === 0
      ? '<p style="color:var(--muted);font-style:italic">Keine Prüfpunkte erfasst</p>'
      : punkte.map((pk) => `
          <div class="pruefpunkt-row">
            <span class="${pk.ok ? 'pruefpunkt-ok' : 'pruefpunkt-fail'}">${pk.ok ? '✅' : '❌'}</span>
            <span style="flex:1">${escH(pk.name || pk.text || JSON.stringify(pk))}</span>
            ${pk.notiz ? `<span style="font-size:11px;color:var(--muted)">${escH(pk.notiz)}</span>` : ''}
          </div>`).join('');

    await modal(`Prüfung – ${p.station || ''}/${p.zimmer || ''} – ${fmtDatum(p.datum)}`, `
      <div class="detail-grid" style="margin-bottom:14px">
        <div class="detail-field"><label>Station</label><div class="val">${escH(p.station || '–')}</div></div>
        <div class="detail-field"><label>Zimmer</label><div class="val">${escH(p.zimmer || '–')}</div></div>
        <div class="detail-field"><label>TV-Typ</label><div class="val">${escH(p.tv_typ || '–')}</div></div>
        <div class="detail-field"><label>Datum</label><div class="val">${fmtDatum(p.datum)}</div></div>
        <div class="detail-field"><label>Prüfer</label><div class="val">${escH(p.mitarbeiter || d.mitarbeiter || '–')}</div></div>
        <div class="detail-field"><label>Zimmer öffnen</label>
          <div class="val">
            ${p.room_id ? `<button class="btn btn-xs btn-secondary" data-open-room="${escH(p.room_id)}">→ Zimmer-Detail</button>` : '–'}
          </div>
        </div>
      </div>
      ${arbeiten.length ? `
        <div class="section-title" style="margin-top:0">Durchgeführte Arbeiten</div>
        <div class="chip-row" style="margin-bottom:14px">${arbeiten.map((a) => `<span class="chip">${escH(a)}</span>`).join('')}</div>` : ''}
      ${material.length ? `
        <div class="section-title">Materialverbrauch</div>
        <div class="chip-row" style="margin-bottom:14px">${material.map((m) => `<span class="chip">${escH(m)}</span>`).join('')}</div>` : ''}
      ${anmerkungen ? `
        <div class="section-title">Anmerkungen</div>
        <div class="lebenslauf" style="max-height:100px;margin-bottom:14px">${escH(anmerkungen)}</div>` : ''}
      <div class="section-title">Prüfpunkte</div>
      <div>${pktHtml}</div>
    `, [{ label: 'Schließen', cls: 'btn-secondary', value: null }]);

    // Zimmer öffnen Event (nach dem Modal geschlossen wird)
    // Wir setzen es direkt im Modal über document.body Delegation
  }

  document.getElementById('pf-laden').addEventListener('click', () => laden(false));
  document.getElementById('pf-alle').addEventListener('click', () => laden(true));

  // Detail-Button einmalig als Delegation (nicht in laden(), sonst stapeln sich
  // die Listener und der Detail-Dialog öffnet sich mehrfach).
  document.getElementById('pf-container').addEventListener('click', (e) => {
    const btn = e.target.closest('[data-detail-uuid]');
    if (btn) { e.stopPropagation(); zeigeDetail(pfRows.find((r) => r.uuid === btn.dataset.detailUuid)); }
  });

  await laden(false);
}

// Zimmer-Link aus dem Prüfungs-Detail-Dialog: einmalig global registriert
// (statt bei jedem Öffnen der Prüfungs-Ansicht erneut → sonst mehrfach geroutet).
document.body.addEventListener('click', (e) => {
  const btn = e.target.closest('[data-open-room]');
  if (btn) {
    window._zimmerDetailId = btn.dataset.openRoom;
    route('zimmer');
  }
});
