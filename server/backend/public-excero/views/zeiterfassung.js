/* global DataGrid, toast, modal, api */
'use strict';

async function viewZeiterfassung() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = '<div class="loading">Lade Zeiterfassung…</div>';

  const heute = new Date();
  const wocheBeginn = new Date(heute);
  wocheBeginn.setDate(heute.getDate() - heute.getDay() + 1);

  const [dZeit, dBau] = await Promise.all([
    api(`/excero/api/web/zeiterfassung?von=${wocheBeginn.toISOString().slice(0,10)}&bis=${heute.toISOString().slice(0,10)}&limit=500`),
    api('/excero/api/web/baustellen'),
  ]);
  const eintraege  = dZeit.eintraege || [];
  const baustellen = dBau.baustellen || [];
  // Mitarbeiternamen aus vorhandenen Einträgen ableiten
  const mitarbeiter = [...new Set(eintraege.map((e) => e.mitarbeiter))].sort().map((n) => ({ name: n, aktiv: true }));

  ca.innerHTML = `
    <div class="view-header">
      <h1>Zeiterfassung</h1>
      <div class="view-header-actions">
        <button class="btn btn-primary btn-sm" id="neu-ze-btn">+ Eintrag</button>
        <button class="btn btn-secondary btn-sm" id="ze-export">XLSX</button>
      </div>
    </div>
    <div class="filter-bar">
      <div class="filter-group"><label>Von</label>
        <input type="date" class="form-control" id="ze-von" value="${wocheBeginn.toISOString().slice(0,10)}"></div>
      <div class="filter-group"><label>Bis</label>
        <input type="date" class="form-control" id="ze-bis" value="${heute.toISOString().slice(0,10)}"></div>
      <button class="btn btn-sm btn-primary" id="ze-laden">Laden</button>
    </div>
    <div id="ze-grid"></div>
    <div id="ze-auswertung" style="margin-top:20px"></div>`;

  function stundenBerechnen(von, bis, pause) {
    if (!von || !bis || !/^\d{2}:\d{2}/.test(von) || !/^\d{2}:\d{2}/.test(bis)) return null;
    const [hV, mV] = von.split(':').map(Number);
    const [hB, mB] = bis.split(':').map(Number);
    const minuten = (hB * 60 + mB) - (hV * 60 + mV) - (Number(pause) || 0);
    return minuten > 0 ? minuten / 60 : 0;
  }

  function renderGrid(daten) {
    const vorhandenerGrid = document.getElementById('ze-grid');
    new DataGrid(vorhandenerGrid, {
      columns: [
        { key: 'datum', label: 'Datum', width: '100px', sort: true },
        { key: 'mitarbeiter', label: 'Mitarbeiter', width: '140px', sort: true },
        { key: 'von', label: 'Von', width: '60px' },
        { key: 'bis', label: 'Bis', width: '60px' },
        { key: '_std', label: 'Stunden', width: '80px', align: 'right',
          render: (_, row) => {
            const std = stundenBerechnen(row.von, row.bis, row.pause_min);
            return std !== null ? `<b>${std.toFixed(2)}</b>` : '–';
          }},
        { key: 'baustelle_name', label: 'Baustelle', width: '140px', sort: true },
        { key: 'taetigkeit', label: 'Tätigkeit', sort: true },
        { key: '_aktion', label: '', width: '60px',
          render: (_, row) => `<button class="btn btn-ghost btn-xs" onclick="editZeit(${row.id})">✏️</button>
            <button class="btn btn-ghost btn-xs" onclick="delZeit(${row.id})">✕</button>` },
      ],
      data: daten,
      filterKeys: ['mitarbeiter', 'datum', 'taetigkeit'],
    });

    // Auswertung nach Mitarbeiter
    const summen = {};
    for (const e of daten) {
      const std = stundenBerechnen(e.von, e.bis, e.pause_min) || 0;
      summen[e.mitarbeiter] = (summen[e.mitarbeiter] || 0) + std;
    }
    const ausEl = document.getElementById('ze-auswertung');
    if (Object.keys(summen).length === 0) { ausEl.innerHTML = ''; return; }
    ausEl.innerHTML = `<h3>Summe im Zeitraum</h3>
      <table class="data-table" style="max-width:400px">
        <thead><tr><th>Mitarbeiter</th><th style="text-align:right">Stunden</th></tr></thead>
        <tbody>${Object.entries(summen).sort((a, b) => a[0].localeCompare(b[0]))
          .map(([m, s]) => `<tr><td>${m}</td><td style="text-align:right"><b>${s.toFixed(2)}</b></td></tr>`).join('')}
        </tbody>
      </table>`;
  }

  renderGrid(eintraege);

  async function laden() {
    const von = document.getElementById('ze-von')?.value;
    const bis = document.getElementById('ze-bis')?.value;
    const d = await api(`/excero/api/web/zeiterfassung?von=${von}&bis=${bis}&limit=500`);
    renderGrid(d.eintraege || []);
  }

  document.getElementById('ze-laden').addEventListener('click', laden);
  document.getElementById('ze-export').addEventListener('click', () => {
    const von = document.getElementById('ze-von')?.value || '';
    const bis = document.getElementById('ze-bis')?.value || '';
    window.open(`/excero/api/web/export/zeiterfassung.xlsx?von=${von}&bis=${bis}`, '_blank');
  });
  document.getElementById('neu-ze-btn').addEventListener('click', () => editZeit(null, baustellen, null));
}

window.editZeit = async function(id, baustellenParam, _unused) {
  const baustellen = baustellenParam || (await api('/excero/api/web/baustellen')).baustellen || [];
  let alt = null;
  if (id) {
    const d = await api(`/excero/api/web/zeiterfassung?limit=1000`);
    alt = (d.eintraege || []).find((e) => e.id === id);
  }

  const bauOpts = `<option value="">Keine Baustelle</option>` +
    baustellen.map((b) => `<option value="${b.id}" ${alt?.baustelle_id === b.id ? 'selected' : ''}>${b.name}</option>`).join('');

  const res = await modal(id ? 'Zeiteintrag bearbeiten' : 'Neuer Zeiteintrag', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Mitarbeiter</label>
        <input class="form-control" id="ze-ma" value="${alt?.mitarbeiter || ''}" placeholder="Name des Mitarbeiters"></div>
      <div class="form-group"><label class="form-label">Datum</label>
        <input class="form-control" type="date" id="ze-datum" value="${alt?.datum || new Date().toISOString().slice(0,10)}"></div>
      <div class="form-group"><label class="form-label">Von</label>
        <input class="form-control" type="time" id="ze-von-i" value="${alt?.von || '08:00'}"></div>
      <div class="form-group"><label class="form-label">Bis</label>
        <input class="form-control" type="time" id="ze-bis-i" value="${alt?.bis || '16:00'}"></div>
      <div class="form-group"><label class="form-label">Pause (Min.)</label>
        <input class="form-control" type="number" id="ze-pause" value="${alt?.pause_min || 0}"></div>
      <div class="form-group"><label class="form-label">Baustelle</label>
        <select class="form-control" id="ze-bau">${bauOpts}</select></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Tätigkeit</label>
        <input class="form-control" id="ze-taet" value="${alt?.taetigkeit || ''}"></div>
      <div class="form-group" style="grid-column:1/-1"><label class="form-label">Bemerkung</label>
        <input class="form-control" id="ze-bem" value="${alt?.bemerkung || ''}"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;

  const body = {
    mitarbeiter: String(document.getElementById('ze-ma')?.value || '').trim(),
    datum: document.getElementById('ze-datum')?.value,
    von: document.getElementById('ze-von-i')?.value,
    bis: document.getElementById('ze-bis-i')?.value,
    pause_min: Number(document.getElementById('ze-pause')?.value) || 0,
    baustelle_id: document.getElementById('ze-bau')?.value || null,
    taetigkeit: document.getElementById('ze-taet')?.value,
    bemerkung: document.getElementById('ze-bem')?.value,
  };
  try {
    if (id) await api(`/excero/api/web/zeiterfassung/${id}`, { method: 'PATCH', body });
    else await api('/excero/api/web/zeiterfassung', { method: 'POST', body });
    toast('Gespeichert');
    viewZeiterfassung();
  } catch (e) { toast(e.message, 'err'); }
};

window.delZeit = async function(id) {
  if (!confirm('Eintrag löschen?')) return;
  await api(`/excero/api/web/zeiterfassung/${id}`, { method: 'DELETE' });
  toast('Gelöscht');
  viewZeiterfassung();
};
