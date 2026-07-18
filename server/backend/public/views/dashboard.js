async function viewDashboard() {
  const area = document.getElementById('content-area');
  area.innerHTML = `
    <div class="page-header"><span class="page-title">Dashboard</span><span class="page-sub">Gesamtübersicht</span></div>
    <div class="kpi-grid" id="kpi-grid">
      <div class="kpi-card"><div class="kpi-val" id="k-zimmer">…</div><div class="kpi-label">Zimmer gesamt</div></div>
      <div class="kpi-card ok"><div class="kpi-val" id="k-pruef">…</div><div class="kpi-label">Prüfungen (30 Tage)</div></div>
      <div class="kpi-card info"><div class="kpi-val" id="k-zettel">…</div><div class="kpi-label">Stundenzettel gesamt</div></div>
      <div class="kpi-card"><div class="kpi-val" id="k-ma">…</div><div class="kpi-label">Mitarbeiter aktiv</div></div>
      <div class="kpi-card warn" id="k-nb-card"><div class="kpi-val" id="k-nb">…</div><div class="kpi-label">Nachbestellung nötig</div></div>
      <div class="kpi-card"><div class="kpi-val" id="k-art">…</div><div class="kpi-label">Lager-Artikel aktiv</div></div>
    </div>
    <div class="card">
      <div class="card-title">🖥 App-Verwaltung &amp; Updater</div>
      <p style="color:var(--muted);font-size:12.5px;margin-bottom:8px;">
        Aktuelle APK-Version für die Android-Geräte. Die App prüft automatisch auf Updates.
      </p>
      <div id="app-version-info" style="font-size:13px;"></div>
    </div>
    <div class="card">
      <div class="card-title">📋 Letzte Prüfungen</div>
      <div class="grid-wrap"><div id="dash-pruef"></div></div>
    </div>`;

  const [rZ, rP, rS, rM, rN, rA, rV] = await Promise.allSettled([
    api('/kkh/api/web/rooms'),
    api('/kkh/api/web/inspections?limit=5'),
    api('/kkh/api/web/stundenzettel'),
    api('/kkh/api/web/mitarbeiter'),
    api('/kkh/api/web/lager/nachbestellung'),
    api('/kkh/api/web/lager/artikel'),
    fetch('/kkh/api/app/version').then((r) => r.ok ? r.json() : null).catch(() => null),
  ]);

  if (rZ.status === 'fulfilled' && rZ.value) document.getElementById('k-zimmer').textContent = rZ.value.rooms?.length ?? '?';
  if (rP.status === 'fulfilled' && rP.value) document.getElementById('k-pruef').textContent = rP.value.inspections?.length ?? '?';
  if (rS.status === 'fulfilled' && rS.value) document.getElementById('k-zettel').textContent = rS.value.zettel?.length ?? '?';
  if (rM.status === 'fulfilled' && rM.value) document.getElementById('k-ma').textContent = (rM.value.mitarbeiter || []).filter((m) => m.aktiv).length;
  if (rN.status === 'fulfilled' && rN.value) {
    const n = rN.value.anzahl ?? 0;
    document.getElementById('k-nb').textContent = n;
    if (n > 0) { document.getElementById('k-nb-card').classList.add('err'); document.getElementById('k-nb-card').classList.remove('warn'); }
    const badge = document.getElementById('nb-badge');
    if (badge) { badge.textContent = n; badge.hidden = n === 0; }
  }
  if (rA.status === 'fulfilled' && rA.value) document.getElementById('k-art').textContent = rA.value.artikel?.length ?? '?';

  const vEl = document.getElementById('app-version-info');
  if (rV.status === 'fulfilled' && rV.value) {
    const v = rV.value;
    vEl.innerHTML = `<div class="code-block">v${escH(v.versionName)} (Code ${escH(String(v.versionCode))}) &nbsp;·&nbsp;
      <a href="/kkh/app/kkh-tv-wartung.apk" class="btn btn-secondary btn-sm">⬇ APK herunterladen</a></div>`;
  } else {
    vEl.innerHTML = '<div class="code-block" style="color:var(--muted)">Keine APK hinterlegt</div>';
  }

  if (rP.status === 'fulfilled' && rP.value?.inspections) {
    new DataGrid('dash-pruef', {
      columns: [
        { key: 'datum', label: 'Datum', cls: 'mono' },
        { key: 'station', label: 'Station' },
        { key: 'zimmer', label: 'Zimmer' },
        { key: 'mitarbeiter', label: 'Prüfer' },
        { key: '_arb', label: 'Arbeiten', render: (r) => {
          const a = r.daten?.arbeiten || [];
          return a.slice(0, 3).map((x) => `<span class="chip">${escH(x)}</span>`).join(' ') + (a.length > 3 ? `<span class="chip">+${a.length - 3}</span>` : '');
        }},
      ],
      rows: (rP.value.inspections || []).map((i) => ({
        ...i, station: i.room?.station ?? i.room_id, zimmer: i.room?.zimmer ?? '',
      })).slice(0, 10),
    });
  }
}
