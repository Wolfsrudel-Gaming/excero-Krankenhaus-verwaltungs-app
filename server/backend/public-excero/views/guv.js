/* global toast, api */
'use strict';

async function viewGuv() {
  const ca = document.getElementById('content-area');
  const heute = new Date();
  const vonDef = `${heute.getFullYear()}-01-01`;
  const bisDef = heute.toISOString().slice(0, 10);
  const firmaId = window.aktiveFirmaId;

  ca.innerHTML = `
    <div class="view-header"><h1>GuV / Finanzauswertung</h1></div>
    <div class="filter-bar">
      <div class="filter-group">
        <label>Von</label>
        <input type="date" class="form-control" id="guv-von" value="${vonDef}">
      </div>
      <div class="filter-group">
        <label>Bis</label>
        <input type="date" class="form-control" id="guv-bis" value="${bisDef}">
      </div>
      <button class="btn btn-primary btn-sm" id="guv-laden">Berechnen</button>
      <button class="btn btn-secondary btn-sm" id="guv-xlsx">XLSX Export</button>
    </div>
    <div id="guv-kpi" class="kpi-grid" style="margin-top:20px"></div>
    <div id="guv-monate" style="margin-top:24px"></div>`;

  async function laden() {
    const von = document.getElementById('guv-von').value;
    const bis = document.getElementById('guv-bis').value;
    const params = new URLSearchParams({ von, bis });
    if (firmaId) params.set('firma_id', firmaId);

    try {
      const [guv, monate] = await Promise.all([
        api(`/excero/api/web/finanzen/guv?${params}`),
        api(`/excero/api/web/finanzen/monate${firmaId ? `?firma_id=${firmaId}` : ''}`),
      ]);

      const fmt = (n) => Number(n || 0).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
      const kpiEl = document.getElementById('guv-kpi');
      const db = guv.deckungsbeitrag || 0;
      kpiEl.innerHTML = `
        <div class="kpi-card green">
          <div class="kpi-label">Einnahmen</div>
          <div class="kpi-value">${fmt(guv.einnahmen)}</div>
          <div class="kpi-sub">Bezahlte Rechnungen</div>
        </div>
        <div class="kpi-card red">
          <div class="kpi-label">Ausgaben</div>
          <div class="kpi-value">${fmt(guv.ausgaben)}</div>
          <div class="kpi-sub">Kosten + Materialien</div>
        </div>
        <div class="kpi-card red">
          <div class="kpi-label">Materialkosten</div>
          <div class="kpi-value">${fmt(guv.materialkosten)}</div>
          <div class="kpi-sub">Lager-Eingänge (EK)</div>
        </div>
        <div class="kpi-card ${db >= 0 ? 'green' : 'red'}">
          <div class="kpi-label">Deckungsbeitrag</div>
          <div class="kpi-value">${fmt(db)}</div>
          <div class="kpi-sub">Einnahmen − Gesamtkosten</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Arbeitsstunden</div>
          <div class="kpi-value">${Number(guv.stunden || 0).toFixed(1)} Std.</div>
          <div class="kpi-sub">Zeiterfassung im Zeitraum</div>
        </div>`;

      const monEl = document.getElementById('guv-monate');
      if ((monate.monate || []).length > 0) {
        monEl.innerHTML = `<h3 style="margin-bottom:10px">Monatsübersicht Einnahmen</h3>
          <table class="data-table"><thead><tr>
            <th>Monat</th><th>Rechnungen</th><th style="text-align:right">Einnahmen</th>
          </tr></thead><tbody>
          ${monate.monate.map((m) => `<tr>
            <td>${m.monat}</td>
            <td>${m.rechnungen}</td>
            <td style="text-align:right">${fmt(m.einnahmen)}</td>
          </tr>`).join('')}
          </tbody></table>`;
      } else {
        monEl.innerHTML = '<p class="empty-hint">Keine bezahlten Rechnungen vorhanden.</p>';
      }
    } catch (e) { toast(e.message, 'err'); }
  }

  document.getElementById('guv-laden').addEventListener('click', laden);
  document.getElementById('guv-xlsx').addEventListener('click', async () => {
    const von = document.getElementById('guv-von').value;
    const bis = document.getElementById('guv-bis').value;
    window.open(`/excero/api/web/export/buchungen.xlsx?von=${von}&bis=${bis}`, '_blank');
  });

  await laden();
}
