/**
 * Dashboard – KKH TV-Wartung
 * Nutzt /api/web/overview für korrekte Aggregat-Daten
 */

async function viewDashboard() {
  const el = document.getElementById('content-area');
  el.innerHTML = '<div class="loading">Dashboard wird geladen…</div>';

  let ov, appInfo;
  try {
    [ov, appInfo] = await Promise.all([
      api('/kkh/api/web/overview'),
      api('/kkh/api/web/app-info').catch(() => null),
    ]);
  } catch (e) {
    el.innerHTML = `<div class="alert alert-err">Fehler beim Laden: ${escH(e.message)}</div>`;
    return;
  }

  const warnungen = [];
  if (ov.freenetAbgelaufen > 0)
    warnungen.push({ typ: 'err', text: `${ov.freenetAbgelaufen} Zimmer mit abgelaufenem Freenet-Vertrag!`, route: 'zimmer' });
  if (ov.freenetBald > 0)
    warnungen.push({ typ: 'warn', text: `${ov.freenetBald} Freenet-Verträge laufen in den nächsten 90 Tagen ab.`, route: 'zimmer' });

  el.innerHTML = `
    ${pageHeader('Dashboard', `
      <a href="/kkh/api/web/export/zimmer" target="_blank" class="btn btn-secondary btn-sm">📊 Export Zimmer</a>
    `)}

    ${warnungen.length ? `
      <div style="display:flex;flex-direction:column;gap:8px;margin-bottom:20px;">
        ${warnungen.map((w) => `
          <div class="alert alert-${w.typ}" style="cursor:pointer;display:flex;align-items:center;gap:8px;" data-warn-route="${w.route}">
            <span>${w.typ === 'err' ? '🔴' : '🟡'}</span>
            <span>${escH(w.text)}</span>
            <span style="margin-left:auto;font-size:12px;opacity:.7">→ Ansehen</span>
          </div>`).join('')}
      </div>` : ''}

    <div class="kpi-grid">
      <div class="kpi-card info" data-kpi-route="zimmer" title="Alle Zimmer anzeigen">
        <div class="kpi-label">Zimmer gesamt</div>
        <div class="kpi-value">${ov.zimmerGesamt}</div>
        <div class="kpi-sub">${ov.zimmerAktiv} aktiv</div>
      </div>
      <div class="kpi-card ${ov.freenetAbgelaufen > 0 ? 'err' : ov.freenetBald > 0 ? 'warn' : 'ok'}" data-kpi-route="zimmer" title="Zimmer mit Freenet-Problemen anzeigen">
        <div class="kpi-label">Freenet abgelaufen</div>
        <div class="kpi-value">${ov.freenetAbgelaufen}</div>
        <div class="kpi-sub">${ov.freenetBald} laufen bald ab</div>
      </div>
      <div class="kpi-card" data-kpi-route="pruefungen">
        <div class="kpi-label">Prüfungen (7 Tage)</div>
        <div class="kpi-value">${ov.pruefungen7}</div>
        <div class="kpi-sub">${ov.pruefungen30} in 30 Tagen</div>
      </div>
      <div class="kpi-card ok" data-kpi-route="pruefungen">
        <div class="kpi-label">Prüfungen gesamt</div>
        <div class="kpi-value">${ov.pruefungenGesamt}</div>
        <div class="kpi-sub">Alle Zeiten</div>
      </div>
    </div>

    ${appInfo ? `
    <div style="display:flex;gap:12px;margin-bottom:24px;flex-wrap:wrap;">
      <div class="settings-section" style="flex:1;min-width:200px;padding:14px 18px;margin:0">
        <div style="font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.5px;color:var(--muted)">Android App</div>
        <div style="font-size:18px;font-weight:700;margin:4px 0 2px">v${escH(appInfo.versionName || '–')}</div>
        <div style="font-size:12px;color:var(--muted)">Code ${appInfo.versionCode || '–'}</div>
        <a href="/kkh/app/app-release.apk" download class="btn btn-secondary btn-sm" style="margin-top:10px">⬇ APK herunterladen</a>
      </div>
    </div>` : ''}

    <div class="section-title">📋 Letzte Prüfungen</div>
    <div class="dg-wrap">
      <table class="data-table">
        <thead><tr>
          <th>Datum</th>
          <th>Station</th>
          <th>Zimmer</th>
          <th>Prüfer</th>
          <th>Durchgeführte Arbeiten</th>
        </tr></thead>
        <tbody>
          ${(ov.letztePruefungen || []).length === 0
            ? '<tr><td colspan="5" class="dg-empty">Keine Prüfungen vorhanden</td></tr>'
            : (ov.letztePruefungen || []).map((p) => {
                const daten = p.daten || {};
                const arbeiten = (daten.arbeiten || []).join(', ') || '–';
                return `<tr class="clickable" data-uuid="${escH(p.uuid)}">
                  <td>${fmtDatum(p.datum)}</td>
                  <td>${escH(p.station || '–')}</td>
                  <td>${escH(p.zimmer || '–')}</td>
                  <td>${escH(p.mitarbeiter || daten.mitarbeiter || '–')}</td>
                  <td style="max-width:300px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${escH(arbeiten)}</td>
                </tr>`;
              }).join('')}
        </tbody>
      </table>
    </div>
  `;

  // KPI-Click Navigation
  el.querySelectorAll('[data-kpi-route]').forEach((card) => {
    card.addEventListener('click', () => route(card.dataset.kpiRoute));
  });
  el.querySelectorAll('[data-warn-route]').forEach((w) => {
    w.addEventListener('click', () => route(w.dataset.warnRoute));
  });
  // Klick auf Prüfung → Prüfungs-View
  el.querySelectorAll('tr[data-uuid]').forEach((tr) => {
    tr.addEventListener('click', () => {
      window.location.hash = 'pruefungen';
      route('pruefungen');
    });
  });
}
