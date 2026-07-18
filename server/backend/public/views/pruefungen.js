/* ── Prüfungen-View ─────────────────────────────────────────────────── */
async function viewPruefungen() {
  const area = document.getElementById('content-area');
  const vonDef = vor30T(), bisDef = heute();
  area.innerHTML = `
    <div class="page-header">
      <span class="page-title">Prüfungen</span>
      <span class="page-sub" id="p-count"></span>
    </div>
    <div class="toolbar">
      <input type="search" id="p-suche" placeholder="Suche Station / Zimmer / Prüfer…">
      <label style="font-size:12px;color:var(--muted)">Von</label>
      <input type="date" id="p-von" value="${vonDef}">
      <label style="font-size:12px;color:var(--muted)">Bis</label>
      <input type="date" id="p-bis" value="${bisDef}">
      <span class="spacer"></span>
      <div id="p-export"></div>
    </div>
    <div class="grid-wrap"><div id="pruef-grid"></div></div>`;

  let alleDaten = [];

  async function lade() {
    const von = document.getElementById('p-von').value;
    const bis = document.getElementById('p-bis').value;
    document.getElementById('p-export').innerHTML = exportBtn('pruefungen', `von=${von}&bis=${bis}`);
    const data = await api(`/kkh/api/web/inspections?limit=1000`);
    if (!data) return;
    alleDaten = (data.inspections || []).filter((i) => {
      if (von && i.datum < von) return false;
      if (bis && i.datum > bis) return false;
      return true;
    });
    render();
  }

  function filtered() {
    const q = document.getElementById('p-suche').value.toLowerCase();
    if (!q) return alleDaten;
    return alleDaten.filter((i) =>
      [i.station, i.zimmer, i.mitarbeiter].some((v) => String(v || '').toLowerCase().includes(q)) ||
      (i.daten?.arbeiten || []).join(' ').toLowerCase().includes(q));
  }

  function render() {
    const rows = filtered();
    document.getElementById('p-count').textContent = `${rows.length} Prüfungen`;
    new DataGrid('pruef-grid', {
      columns: [
        { key: 'datum', label: 'Datum', cls: 'mono' },
        { key: 'station', label: 'Station' },
        { key: 'zimmer', label: 'Zimmer' },
        { key: 'mitarbeiter', label: 'Prüfer' },
        { key: '_arb', label: 'Arbeiten / Material', render: (r) =>
          (r.daten?.arbeiten || []).map((a) => `<span class="chip">${escH(a)}</span>`).join(' ') || '–' },
        { key: '_bem', label: 'Bemerkungen', render: (r) => escH(r.daten?.bemerkungen || '') || '–' },
      ],
      rows: rows.map((i) => ({
        ...i, _key: i.uuid,
        station: i.station ?? i.room_id, zimmer: i.zimmer ?? '',
      })),
    });
  }

  await lade();
  document.getElementById('p-suche').addEventListener('input', render);
  ['p-von', 'p-bis'].forEach((id) => document.getElementById(id).addEventListener('change', lade));
}
