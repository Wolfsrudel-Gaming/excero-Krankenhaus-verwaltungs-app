/* ── Stundenzettel-Views ─────────────────────────────────────────────── */
async function viewStundenzettel() {
  const area = document.getElementById('content-area');
  area.innerHTML = `
    <div class="page-header">
      <span class="page-title">Stundenzettel</span>
      <span class="page-sub" id="sz-count"></span>
    </div>
    <div class="toolbar">
      <input type="search" id="sz-suche" placeholder="Suche Station / Techniker / Auftragsnr.…">
      <span class="spacer"></span>
      ${exportBtn('stundenzettel')}
      <button class="btn btn-primary" id="sz-neu">+ Neu</button>
    </div>
    <div class="grid-wrap"><div id="sz-grid"></div></div>`;

  const data = await api('/kkh/api/web/stundenzettel');
  if (!data) return;
  let alle = data.zettel || [];
  document.getElementById('sz-count').textContent = `${alle.length} Stundenzettel`;

  function filtered() {
    const q = document.getElementById('sz-suche').value.toLowerCase();
    if (!q) return alle;
    return alle.filter((z) =>
      [z.station, z.techniker, z.auftragsnummer].some((v) => String(v || '').toLowerCase().includes(q)));
  }

  let grid;

  function render() {
    const rows = filtered();
    grid = new DataGrid('sz-grid', {
      columns: [
        { key: 'zeitraum_start', label: 'Zeitraum ab', cls: 'mono' },
        { key: 'station', label: 'Station' },
        { key: 'auftragsnummer', label: 'Auftragsnr.' },
        { key: 'techniker', label: 'Techniker' },
        { key: 'datum', label: 'Datum', cls: 'mono' },
        { key: 'stunden', label: 'Std. (Kopf)', num: true },
        { key: 'anfahrt', label: 'Anfahrt (Kopf)', num: true },
        { key: '_team_anz', label: 'Team', num: true, render: (r) => String(r.team?.anzahl ?? 0) },
        { key: '_team_std', label: 'Team-Std.', num: true, render: (r) =>
          r.team?.stunden ? fmt(r.team.stunden, 'num') : '–' },
        { key: '_pdf', label: 'PDF', render: (r) => {
          const station = (r.station || '').replace(/[^A-Za-z0-9äöüÄÖÜß_-]/g, '_');
          return `<a class="pdf-link" href="/kkh/api/web/stundenzettel/pdf?station=${encodeURIComponent(r.station)}&zeitraum=${encodeURIComponent(r.zeitraum_start)}" target="_blank" title="PDF öffnen">📄</a>`;
        }},
      ],
      rows,
      rowClick: (r) => viewStundenzettelDetail(r.station, r.zeitraum_start),
    });
  }

  render();
  document.getElementById('sz-suche').addEventListener('input', render);
  document.getElementById('sz-neu').addEventListener('click', () => modalStundenzettelNeu());
}

async function viewStundenzettelDetail(station, zeitraumStart) {
  const area = document.getElementById('content-area');
  area.innerHTML = `<div class="page-header"><span class="page-title">Stundenzettel wird geladen…</span></div>`;
  const data = await api(`/kkh/api/web/stundenzettel/detail?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraumStart)}`);
  if (!data) return;
  const z = data.zettel;
  const eintraege = data.eintraege || [];

  const pdfUrl = `/kkh/api/web/stundenzettel/pdf?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraumStart)}`;

  area.innerHTML = `
    <a class="detail-back" href="#" id="sz-back">← Zurück zu Stundenzettel</a>
    <div class="page-header">
      <span class="page-title">${escH(z.station)} · ${escH(z.zeitraum_start)}</span>
      <span class="page-sub">${escH(z.auftragsnummer || '–')}</span>
    </div>

    <div class="card">
      <div class="card-title">Kopfdaten
        <span style="margin-left:auto;display:flex;gap:6px;">
          <a class="btn btn-secondary btn-sm" href="${pdfUrl}" target="_blank">📄 PDF öffnen</a>
          <button class="btn btn-primary btn-sm" id="sz-save">💾 Speichern</button>
          <button class="btn btn-danger btn-sm" id="sz-del">Löschen</button>
        </span>
      </div>
      <div class="form-grid">
        <div class="form-group"><label class="form-label">Auftragsnummer</label>
          <input class="form-control" id="sz-nr" value="${escH(z.auftragsnummer)}"></div>
        <div class="form-group"><label class="form-label">Station</label>
          <input class="form-control" id="sz-sta" value="${escH(z.station)}"></div>
        <div class="form-group"><label class="form-label">Zeitraum ab</label>
          <input class="form-control mono" id="sz-zr" value="${escH(z.zeitraum_start)}"></div>
        <div class="form-group"><label class="form-label">Datum</label>
          <input class="form-control mono" id="sz-dat" value="${escH(z.datum)}"></div>
        <div class="form-group"><label class="form-label">Stunden (Kopf)</label>
          <input class="form-control" id="sz-std" value="${escH(z.stunden)}"></div>
        <div class="form-group"><label class="form-label">Anfahrt (Kopf)</label>
          <input class="form-control" id="sz-anf" value="${escH(z.anfahrt)}"></div>
        <div class="form-group"><label class="form-label">Techniker</label>
          <input class="form-control" id="sz-tech" value="${escH(z.techniker)}"></div>
      </div>
    </div>

    <div class="card">
      <div class="card-title">Team-Einträge
        <span style="margin-left:auto"><button class="btn btn-secondary btn-sm" id="sze-add">+ Eintrag</button></span>
      </div>
      <div class="grid-wrap"><div id="sze-grid"></div></div>
    </div>`;

  document.getElementById('sz-back').addEventListener('click', (e) => { e.preventDefault(); viewStundenzettel(); });

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

  document.getElementById('sz-del').addEventListener('click', async () => {
    if (!(await confirm(`Stundenzettel ${station} / ${zeitraumStart} wirklich löschen?`))) return;
    try {
      await api(`/kkh/api/web/stundenzettel?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraumStart)}`, { method: 'DELETE' });
      toast('Stundenzettel gelöscht');
      viewStundenzettel();
    } catch (e) { toast(e.message, 'err'); }
  });

  function renderEintraege(rows) {
    new DataGrid('sze-grid', {
      columns: [
        { key: 'mitarbeiter', label: 'Mitarbeiter' },
        { key: 'stunden', label: 'Stunden', num: true },
        { key: 'anfahrt', label: 'Anfahrt', num: true },
      ],
      rows,
      actions: (e) => `
        <button class="btn btn-secondary btn-sm" onclick="bearbeiteZettelEintrag('${escH(station)}','${escH(zeitraumStart)}','${escH(e.mitarbeiter)}',this)">Bearb.</button>
        <button class="btn btn-danger btn-sm" onclick="loescheZettelEintrag('${escH(station)}','${escH(zeitraumStart)}','${escH(e.mitarbeiter)}')">Löschen</button>`,
    });
  }
  renderEintraege(eintraege);

  document.getElementById('sze-add').addEventListener('click', async () => {
    const m = await modal('Team-Eintrag hinzufügen', `
      <div class="form-grid">
        <div class="form-group"><label class="form-label">Mitarbeiter <span class="req">*</span></label>
          <input class="form-control" id="ze-ma"></div>
        <div class="form-group"><label class="form-label">Stunden</label>
          <input class="form-control" id="ze-std"></div>
        <div class="form-group"><label class="form-label">Anfahrt</label>
          <input class="form-control" id="ze-anf"></div>
      </div>`,
      [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
    if (m !== 'ok') return;
    try {
      await api('/kkh/api/web/zettel-eintraege', { method: 'PUT', body: {
        station, zeitraumStart,
        mitarbeiter: document.getElementById('ze-ma')?.value,
        stunden: document.getElementById('ze-std')?.value || '',
        anfahrt: document.getElementById('ze-anf')?.value || '',
      }});
      toast('Eintrag gespeichert');
      viewStundenzettelDetail(station, zeitraumStart);
    } catch (e) { toast(e.message, 'err'); }
  });
}

window.bearbeiteZettelEintrag = async function(station, zeitraum, mitarbeiter) {
  const m = await modal(`Eintrag: ${mitarbeiter}`, `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Stunden</label>
        <input class="form-control" id="ze-std"></div>
      <div class="form-group"><label class="form-label">Anfahrt</label>
        <input class="form-control" id="ze-anf"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (m !== 'ok') return;
  try {
    await api('/kkh/api/web/zettel-eintraege', { method: 'PUT', body: {
      station, zeitraumStart: zeitraum, mitarbeiter,
      stunden: document.getElementById('ze-std')?.value || '',
      anfahrt: document.getElementById('ze-anf')?.value || '',
    }});
    toast('Eintrag gespeichert');
    viewStundenzettelDetail(station, zeitraum);
  } catch (e) { toast(e.message, 'err'); }
};

window.loescheZettelEintrag = async function(station, zeitraum, mitarbeiter) {
  if (!(await confirm(`Eintrag von ${mitarbeiter} löschen?`))) return;
  try {
    await api(`/kkh/api/web/zettel-eintraege?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraum)}&mitarbeiter=${encodeURIComponent(mitarbeiter)}`, { method: 'DELETE' });
    toast('Eintrag gelöscht');
    viewStundenzettelDetail(station, zeitraum);
  } catch (e) { toast(e.message, 'err'); }
};

async function modalStundenzettelNeu() {
  const nrData = await api('/kkh/api/web/stundenzettel/next-nr').catch(() => null);
  const nr = nrData?.nr || '';
  const res = await modal('Neuer Stundenzettel', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Auftragsnummer</label>
        <input class="form-control" id="m-nr" value="${escH(nr)}"></div>
      <div class="form-group"><label class="form-label">Station <span class="req">*</span></label>
        <input class="form-control" id="m-sta"></div>
      <div class="form-group"><label class="form-label">Zeitraum ab <span class="req">*</span></label>
        <input type="date" class="form-control" id="m-zr" value="${heute()}"></div>
      <div class="form-group"><label class="form-label">Techniker</label>
        <input class="form-control" id="m-tech"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  try {
    await api('/kkh/api/web/stundenzettel', { method: 'PUT', body: {
      auftragsnummer: document.getElementById('m-nr')?.value || '',
      station: document.getElementById('m-sta')?.value || '',
      zeitraumStart: document.getElementById('m-zr')?.value || '',
      techniker: document.getElementById('m-tech')?.value || '',
      datum: '', stunden: '', anfahrt: '',
    }});
    toast('Stundenzettel angelegt');
    viewStundenzettel();
  } catch (e) { toast(e.message, 'err'); }
}
