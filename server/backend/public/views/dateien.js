/**
 * Dateien & Fotos – KKH TV-Wartung
 * - Galerie-Ansicht für Prüfungsfotos (Thumbnails)
 * - Dateiliste für Prüfbericht-PDFs
 * - ZIP-Download nach Station/Zeitraum
 *
 * HINWEIS: Unterschriften/Signaturen werden ausschließlich serverseitig
 * für die PDF-Generierung verarbeitet und sind im Web-Panel nicht sichtbar.
 */

async function viewDateien() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Dateien & Fotos', `
      <button class="btn btn-secondary btn-sm" id="df-reload">🔄 Aktualisieren</button>
    `)}

    <div class="filter-bar">
      <div class="filter-group"><label>Station (für ZIP)</label>
        <input type="text" class="form-control form-control-sm" id="df-station" placeholder="Alle" style="width:110px"></div>
      <div class="filter-group"><label>Von</label>
        <input type="date" class="form-control form-control-sm" id="df-von" value="${monatVon()}"></div>
      <div class="filter-group"><label>Bis</label>
        <input type="date" class="form-control form-control-sm" id="df-bis" value="${heute()}"></div>
      <div class="filter-group"><label>&nbsp;</label>
        <a class="btn btn-primary btn-sm" id="df-zip-link" href="#" target="_blank">⬇ Fotos als ZIP</a>
      </div>
    </div>

    <div class="tab-nav" id="df-tabs">
      <button class="tab-btn" data-tab="fotos">Prüfungsfotos</button>
      <button class="tab-btn" data-tab="pdfs">Prüfbericht-PDFs</button>
    </div>

    <div data-panel="fotos" id="df-fotos-panel">
      <div class="loading">Fotos werden geladen…</div>
    </div>
    <div data-panel="pdfs" id="df-pdfs-panel" hidden>
      <div class="loading">PDFs werden geladen…</div>
    </div>

    <div class="section-title" style="margin-top:20px">☁️ HiDrive-Export (optional)</div>
    <div class="card" style="padding:14px;display:flex;flex-direction:column;gap:10px">
      <p style="font-size:12px;color:var(--muted);margin:0">
        Legt Fotos und Prüfberichte je Zimmer/Tag in HiDrive ab – Struktur
        <code>/Fotos_Zimmer/&lt;Station_Zimmer&gt;/&lt;JJJJMMTT&gt;/</code> (wie im ZIP).
        Braucht die HiDrive-<strong>WebDAV</strong>-Zugangsdaten (nicht den Freigabe-Link).
      </p>
      <div id="hd-status" style="font-size:13px"></div>
      <details id="hd-config-box">
        <summary style="cursor:pointer;font-weight:600">Zugangsdaten (WebDAV)</summary>
        <div class="form-grid" style="margin-top:10px">
          <div class="form-group full"><label class="form-label">WebDAV-URL</label>
            <input class="form-control" id="hd-url" placeholder="https://webdav.hidrive.ionos.com/"></div>
          <div class="form-group"><label class="form-label">Benutzer</label>
            <input class="form-control" id="hd-user" autocapitalize="none"></div>
          <div class="form-group"><label class="form-label">App-Passwort</label>
            <input type="password" class="form-control" id="hd-pw" placeholder="••••••••"></div>
        </div>
        <div style="display:flex;gap:8px;margin-top:8px">
          <button class="btn btn-secondary btn-sm" id="hd-save">Speichern</button>
          <button class="btn btn-ghost btn-sm" id="hd-test">Verbindung testen</button>
        </div>
      </details>
      <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
        <label style="font-size:13px"><input type="checkbox" id="hd-alle"> alle erneut hochladen</label>
        <button class="btn btn-primary" id="hd-export">Nach HiDrive exportieren</button>
        <span id="hd-result" style="font-size:13px;color:var(--muted)"></span>
      </div>
      <p style="font-size:11px;color:var(--muted);margin:0">
        Es werden die oben gewählten Filter (Station/Von/Bis) angewandt. „alle erneut" lädt auch
        bereits vorhandene Dateien neu; sonst werden nur fehlende/geänderte übertragen.
      </p>
    </div>
  `;

  // Tabs initialisieren
  const tabContainer = el.querySelector('#df-tabs').parentElement;
  initTabs(tabContainer);

  // HiDrive-Konfiguration + Export
  (async () => {
    const statusEl = document.getElementById('hd-status');
    async function ladeConfig() {
      try {
        const c = await api('/kkh/api/web/hidrive/config');
        document.getElementById('hd-url').value = c.url || '';
        document.getElementById('hd-user').value = c.user || '';
        document.getElementById('hd-pw').value = c.konfiguriert ? '••••••••' : '';
        statusEl.innerHTML = c.konfiguriert
          ? badge('Zugang hinterlegt', 'ok')
          : badge('Noch keine Zugangsdaten', 'warn');
        if (!c.konfiguriert) document.getElementById('hd-config-box').open = true;
      } catch (e) { statusEl.innerHTML = `<span class="alert-err">${escH(e.message)}</span>`; }
    }
    await ladeConfig();

    document.getElementById('hd-save')?.addEventListener('click', async () => {
      try {
        await api('/kkh/api/web/hidrive/config', { method: 'POST', body: {
          url: document.getElementById('hd-url').value.trim(),
          user: document.getElementById('hd-user').value.trim(),
          passwort: document.getElementById('hd-pw').value,
        }});
        toast('HiDrive-Zugang gespeichert');
        ladeConfig();
      } catch (e) { toast(e.message, 'err'); }
    });

    document.getElementById('hd-test')?.addEventListener('click', async () => {
      const r = document.getElementById('hd-result');
      r.textContent = 'Teste…';
      try {
        const d = await api('/kkh/api/web/hidrive/test');
        r.textContent = d.ok ? '✅ Verbindung ok' : `⚠️ Server antwortete mit ${d.status}`;
      } catch (e) { r.textContent = '❌ ' + e.message; }
    });

    document.getElementById('hd-export')?.addEventListener('click', async () => {
      const btn = document.getElementById('hd-export');
      const r = document.getElementById('hd-result');
      const params = new URLSearchParams();
      const station = document.getElementById('df-station')?.value?.trim();
      const von = document.getElementById('df-von')?.value;
      const bis = document.getElementById('df-bis')?.value;
      if (station) params.set('station', station);
      if (von) params.set('von', von);
      if (bis) params.set('bis', bis);
      if (document.getElementById('hd-alle')?.checked) params.set('alle', '1');
      btn.disabled = true; r.textContent = 'Export läuft… (bitte warten)';
      try {
        const d = await api(`/kkh/api/web/hidrive/export?${params}`, { method: 'POST' });
        r.textContent = `✅ ${d.hochgeladen} hochgeladen, ${d.uebersprungen} übersprungen`
          + (d.fehler ? `, ${d.fehler} Fehler` : '') + ` (${d.gesamt} Dateien, ${d.ordner} Ordner)`;
        toast('HiDrive-Export fertig');
      } catch (e) { r.textContent = '❌ ' + e.message; toast(e.message, 'err'); }
      finally { btn.disabled = false; }
    });
  })();

  // ZIP-Link aktualisieren
  function aktualisiereZipLink() {
    const station = document.getElementById('df-station')?.value?.trim();
    const von = document.getElementById('df-von')?.value;
    const bis = document.getElementById('df-bis')?.value;
    const params = new URLSearchParams();
    if (station) params.set('station', station);
    if (von) params.set('von', von);
    if (bis) params.set('bis', bis);
    const link = document.getElementById('df-zip-link');
    if (link) link.href = `/kkh/api/web/export/fotos.zip?${params}`;
  }

  ['df-station', 'df-von', 'df-bis'].forEach((id) =>
    document.getElementById(id)?.addEventListener('input', aktualisiereZipLink));
  aktualisiereZipLink();
  // Stations-Filter auch auf die angezeigte Galerie/PDF-Liste anwenden
  document.getElementById('df-station')?.addEventListener('input', () => ladeDateien());

  // Dateien laden
  async function ladeDateien() {
    document.getElementById('df-fotos-panel').innerHTML = '<div class="loading">Fotos werden geladen…</div>';
    document.getElementById('df-pdfs-panel').innerHTML = '<div class="loading">PDFs werden geladen…</div>';

    let allFiles;
    try {
      const d = await api('/kkh/api/web/files');
      allFiles = d?.files || [];
    } catch (e) {
      el.querySelectorAll('[data-panel]').forEach((p) => {
        p.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`;
      });
      return;
    }

    // Optionaler Stations-Filter (aus dem Filterfeld) auf die Anzeige anwenden
    const stationFilter = document.getElementById('df-station')?.value?.trim().toLowerCase();
    if (stationFilter) {
      allFiles = allFiles.filter((f) => (f.station || '').toLowerCase().includes(stationFilter)
        || (f.roomId || '').toLowerCase().includes(stationFilter));
    }

    // Fotos (JPEG/PNG) und PDFs; Signaturen sind serverseitig bereits ausgeschlossen
    const fotos = allFiles.filter((f) => /\.(jpe?g|png)$/i.test(f.path));
    const pdfs = allFiles.filter((f) => f.path.endsWith('.pdf'));

    // Fotos-Galerie
    const fotoPanel = document.getElementById('df-fotos-panel');
    if (fotoPanel) {
      if (fotos.length === 0) {
        fotoPanel.innerHTML = `
          <div class="alert alert-info">
            <strong>Keine Prüfungsfotos gefunden.</strong><br>
            Fotos werden über die App hochgeladen. Der ZIP-Download oben enthält alle Fotos des gewählten Zeitraums.
          </div>`;
      } else {
        fotoPanel.innerHTML = `
          <p style="font-size:12px;color:var(--muted);margin-bottom:12px">
            ${fotos.length} Foto(s)${stationFilter ? ' (gefiltert)' : ''}
          </p>
          <div class="foto-grid">
            ${fotos.map((f) => `
              <div class="foto-thumb">
                <a href="/kkh/api/web/file?path=${encodeURIComponent(f.path)}" target="_blank" title="${escH(f.path)}">
                  <img src="/kkh/api/web/thumb?path=${encodeURIComponent(f.path)}" alt="${escH(f.path.split('/').pop())}" loading="lazy">
                </a>
                <div class="foto-name">${escH(f.station || '')} / ${escH(f.zimmer || '')}</div>
              </div>`).join('')}
          </div>`;
      }
    }

    // PDFs-Liste
    const pdfPanel = document.getElementById('df-pdfs-panel');
    if (pdfPanel) {
      if (pdfs.length === 0) {
        pdfPanel.innerHTML = `
          <div class="alert alert-info">
            Keine gespeicherten PDFs gefunden. Prüfberichte können jederzeit über den
            <strong>Stundenzettel-View</strong> oder den <strong>Zimmer-Detail</strong> als PDF abgerufen werden.
          </div>`;
      } else {
        pdfPanel.innerHTML = '';
        new DataGrid(pdfPanel, {
          data: pdfs,
          filterKeys: ['station', 'zimmer', 'path'],
          columns: [
            { key: 'station', label: 'Station', sort: true, width: '90px' },
            { key: 'zimmer', label: 'Zimmer', sort: true, width: '80px' },
            { key: 'path', label: 'Dateiname',
              render: (v) => escH(v.split('/').pop()) },
            { key: 'path', label: '', width: '100px', align: 'center',
              render: (v) => `<a class="btn btn-xs btn-secondary" href="/kkh/api/web/file?path=${encodeURIComponent(v)}" target="_blank">📄 Öffnen</a>` },
          ],
        });
      }
    }
  }

  document.getElementById('df-reload')?.addEventListener('click', ladeDateien);
  await ladeDateien();
}
