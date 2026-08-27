/**
 * KI-Prüfung: Foto-Analysen, Abweichungen bestätigen, Modell-Status.
 * Jede Bestätigung wird Trainingslabel – das Netz lernt mit jedem Klick.
 */

const KI_STATUS_BADGE = {
  wartet: () => badge('Wartet', 'gray'),
  laeuft: () => badge('Läuft…', 'info'),
  uebereinstimmung: () => badge('Stimmt überein', 'ok'),
  abweichung: () => badge('Abweichung', 'err'),
  unlesbar: () => badge('Unlesbar', 'warn'),
  fehler: () => badge('Fehler', 'err'),
};

const KI_FELD_NAMEN = {
  seriennummer: 'Seriennummer',
  freenet_id: 'Freenet-ID',
  gueltig_bis: 'Gültig bis',
  tv_typ: 'TV-Typ',
};

async function viewKi() {
  const content = document.getElementById('content-area');
  content.innerHTML = pageHeader('KI-Prüfung', `
    <button class="btn btn-secondary btn-sm" id="ki-train-btn">Training starten</button>
    <button class="btn btn-primary btn-sm" id="ki-reload-btn">Aktualisieren</button>
  `) + `
    <div class="kpi-row" id="ki-kpis">${skeleton()}</div>
    <div class="filter-bar">
      <div class="filter-group"><label>Status</label>
        <select class="form-control" id="ki-f-status">
          <option value="">Alle</option>
          <option value="abweichung" selected>Abweichungen</option>
          <option value="uebereinstimmung">Übereinstimmungen</option>
          <option value="unlesbar">Unlesbar</option>
          <option value="wartet">Wartend</option>
          <option value="fehler">Fehler</option>
        </select></div>
    </div>
    <div id="ki-grid">${skeleton()}</div>`;

  document.getElementById('ki-train-btn').addEventListener('click', kiTrainingStarten);
  document.getElementById('ki-reload-btn').addEventListener('click', () => viewKi());
  document.getElementById('ki-f-status').addEventListener('change', ladeKiAnalysen);

  await Promise.all([ladeKiStatus(), ladeKiAnalysen()]);
}

async function ladeKiStatus() {
  const el = document.getElementById('ki-kpis');
  try {
    const s = await api('/kkh/api/web/ki/status');
    if (!s || s.offline) {
      el.innerHTML = `<div class="alert alert-warn">KI-Service ist gerade nicht erreichbar –
        Analysen laufen weiter, sobald der Container gestartet ist.</div>`;
      return;
    }
    const w = s.warteschlange || {};
    const modelle = (s.modelle || []).map((m) =>
      `${escH(m.name)} v${escH(m.version)} (${Math.round((m.genauigkeit || 0) * 100)}%)`).join(' · ')
      || 'Noch kein Training – Regeln/Heuristik aktiv';
    el.innerHTML = `
      <div class="kpi-card"><div class="kpi-value">${w.wartet || 0}</div><div class="kpi-label">In Warteschlange</div></div>
      <div class="kpi-card ${w.abweichung ? 'kpi-warn' : ''}"><div class="kpi-value">${w.abweichung || 0}</div><div class="kpi-label">Abweichungen</div></div>
      <div class="kpi-card"><div class="kpi-value">${w.uebereinstimmung || 0}</div><div class="kpi-label">Bestätigt korrekt</div></div>
      <div class="kpi-card"><div class="kpi-value">${s.labels_gesamt || 0}</div><div class="kpi-label">Trainingslabels</div></div>
      <div class="kpi-card" style="grid-column:span 2"><div class="kpi-label">Aktive Modelle</div>
        <div style="font-size:13px;margin-top:4px">${modelle}</div></div>`;
  } catch (e) {
    el.innerHTML = `<div class="alert alert-warn">${escH(e.message)}</div>`;
  }
}

async function ladeKiAnalysen() {
  const status = document.getElementById('ki-f-status')?.value ?? '';
  const grid = document.getElementById('ki-grid');
  if (!grid) return;
  try {
    const d = await api(`/kkh/api/web/ki/analysen${status ? `?status=${status}` : ''}`);
    const rows = (d?.analysen || []).map((a) => ({
      ...a,
      datei: a.pfad.split('/').pop(),
      erkannt: felderKurz(a.felder),
      _rowClass: a.status === 'abweichung' ? 'warn-row' : '',
    }));
    grid.innerHTML = '';
    new DataGrid(grid, {
      data: rows,
      filterKeys: ['pfad', 'room_id', 'bildtyp', 'erkannt'],
      columns: [
        { key: 'pfad', label: 'Foto', width: '90px', render: (v) => `
          <img src="/kkh/api/web/thumb?path=${encodeURIComponent(v)}" loading="lazy"
               style="width:72px;height:54px;object-fit:cover;border-radius:6px" alt="">` },
        { key: 'room_id', label: 'Zimmer', sort: true },
        { key: 'bildtyp', label: 'Bildtyp', sort: true, render: (v) => v
          ? badge({ menue: 'CI-Menü', geraet: 'Gerät', uebersicht: 'Übersicht' }[v] || v, 'teal') : '–' },
        { key: 'erkannt', label: 'Erkannte Werte' },
        { key: 'status', label: 'Status', sort: true,
          render: (v) => (KI_STATUS_BADGE[v] || (() => escH(v)))() },
        { key: 'analysiert_am', label: 'Analysiert', sort: true,
          render: (v) => v ? fmtDatum(String(v).slice(0, 10)) : '–' },
      ],
      onRowClick: (row) => zeigeKiDetail(row),
    });
  } catch (e) {
    grid.innerHTML = `<div class="alert alert-warn">${escH(e.message)}</div>`;
  }
}

function felderKurz(felder) {
  if (!felder) return '';
  return Object.entries(felder)
    .filter(([k]) => !k.startsWith('_'))
    .map(([k, v]) => `${KI_FELD_NAMEN[k] || k}: ${v.wert}`)
    .join(' · ');
}

// Delegierter Listener (einmalig): "Manuell eingeben" blendet das Feld ein,
// solange das Modal offen ist.
document.addEventListener('change', (e) => {
  if (!e.target.id || !e.target.id.startsWith('ki-e-')) return;
  const inp = document.getElementById(e.target.id.replace('ki-e-', 'ki-m-'));
  if (inp) inp.hidden = e.target.value !== 'manuell';
});

async function zeigeKiDetail(analyse) {
  const felder = analyse.felder || {};
  const abgleich = analyse.abgleich || {};
  const alleFelder = ['seriennummer', 'freenet_id', 'gueltig_bis', 'tv_typ'];

  const zeilen = alleFelder.map((feld) => {
    const erkannt = felder[feld]?.wert || '';
    const konf = felder[feld]?.konfidenz;
    const a = abgleich[feld] || {};
    const stamm = a.stammdaten ?? '';
    if (!erkannt && !stamm) return '';
    const statusBadge = a.passt === true ? badge('passt', 'ok')
      : a.passt === false ? badge('Abweichung', 'err')
      : badge('kein Vergleich', 'gray');
    return `
      <tr>
        <td><b>${KI_FELD_NAMEN[feld]}</b></td>
        <td>${escH(erkannt) || '–'}${konf ? ` <small style="color:var(--gray)">(${Math.round(konf * 100)}%)</small>` : ''}</td>
        <td>${escH(stamm) || '–'}</td>
        <td>${statusBadge}</td>
        <td>
          <select class="form-control" id="ki-e-${feld}" style="min-width:150px">
            <option value="">– keine Änderung –</option>
            ${erkannt ? `<option value="ki">KI hat recht${a.passt === false ? ' (Stammdaten korrigieren)' : ''}</option>` : ''}
            ${stamm ? `<option value="stamm">Stammdaten stimmen</option>` : ''}
            <option value="manuell">Manuell eingeben…</option>
          </select>
          <input class="form-control" id="ki-m-${feld}" placeholder="richtiger Wert" style="margin-top:4px" hidden>
        </td>
      </tr>`;
  }).filter(Boolean).join('');

  const res = await modal(`KI-Analyse: ${analyse.room_id || analyse.pfad}`, `
    <div style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:12px">
      <img src="/kkh/api/web/file?path=${encodeURIComponent(analyse.pfad)}"
           style="max-width:340px;max-height:280px;border-radius:8px;object-fit:contain" alt="Foto">
      <div style="flex:1;min-width:220px">
        <p><b>Datei:</b> ${escH(analyse.pfad)}</p>
        <p><b>Bildtyp:</b> ${escH(analyse.bildtyp || '–')} · <b>Modell:</b> ${escH(analyse.modell_version || '–')}</p>
        ${analyse.fehler ? `<p class="alert alert-warn">${escH(analyse.fehler)}</p>` : ''}
      </div>
    </div>
    ${zeilen ? `<table class="data-table"><thead><tr>
        <th>Feld</th><th>KI erkannt</th><th>Stammdaten</th><th>Abgleich</th><th>Entscheidung</th>
      </tr></thead><tbody>${zeilen}</tbody></table>`
      : '<p>Keine Felder erkannt. Bei Übersichtsfotos ist das normal.</p>'}
    <p style="color:var(--gray);font-size:13px;margin-top:10px">
      Jede Entscheidung wird als Trainingsbeispiel gespeichert – die KI wird dadurch besser.</p>`,
    [
      { label: 'Schließen', value: null, cls: 'btn-secondary' },
      { label: 'Neu analysieren', value: 'neu', cls: 'btn-secondary' },
      { label: 'Entscheidungen speichern', value: 'ok', cls: 'btn-primary' },
    ]);

  if (!res) return;
  if (res.action === 'neu') {
    try {
      await api(`/kkh/api/web/ki/analysen/${analyse.id}/neu`, { method: 'POST' });
      toast('Foto wird neu analysiert');
      ladeKiAnalysen();
    } catch (e) { toast(e.message, 'err'); }
    return;
  }
  if (res.action !== 'ok') return;

  const entscheidungen = {};
  for (const feld of alleFelder) {
    const wahl = mf(res, `ki-e-${feld}`);
    if (!wahl) continue;
    const erkannt = felder[feld]?.wert || '';
    const stamm = (abgleich[feld] || {}).stammdaten || '';
    if (wahl === 'ki' && erkannt) {
      entscheidungen[feld] = { wert: erkannt, stammdatenUebernehmen: true };
    } else if (wahl === 'stamm' && stamm) {
      entscheidungen[feld] = { wert: stamm, stammdatenUebernehmen: false };
    } else if (wahl === 'manuell') {
      const wert = (mf(res, `ki-m-${feld}`) || '').trim();
      if (wert) entscheidungen[feld] = { wert, stammdatenUebernehmen: true };
    }
  }
  if (!Object.keys(entscheidungen).length) { toast('Keine Entscheidungen getroffen', 'warn'); return; }
  try {
    await api(`/kkh/api/web/ki/analysen/${analyse.id}/bestaetigen`, {
      method: 'POST', body: { entscheidungen } });
    toast('Gespeichert – wird als Trainingsbeispiel genutzt');
    ladeKiAnalysen();
    ladeKiStatus();
  } catch (e) { toast(e.message, 'err'); }
}

async function kiTrainingStarten() {
  const btn = document.getElementById('ki-train-btn');
  btn.disabled = true; btn.textContent = 'Training läuft…';
  try {
    const r = await api('/kkh/api/web/ki/train', { method: 'POST' });
    toast(r?.status === 'fertig' ? 'Training abgeschlossen' : (r?.status || 'Training angestoßen'));
    ladeKiStatus();
  } catch (e) { toast(e.message, 'err'); }
  finally { btn.disabled = false; btn.textContent = 'Training starten'; }
}
