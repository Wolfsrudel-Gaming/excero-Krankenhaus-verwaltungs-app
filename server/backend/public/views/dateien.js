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
  `;

  // Tabs initialisieren
  const tabContainer = el.querySelector('#df-tabs').parentElement;
  initTabs(tabContainer);

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

  // Dateien laden
  async function ladeDateien() {
    let rooms;
    try {
      const d = await api('/kkh/api/web/rooms');
      rooms = d?.rooms || [];
    } catch (e) {
      el.querySelectorAll('[data-panel]').forEach((p) => {
        p.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`;
      });
      return;
    }

    const allFiles = [];
    // Erste 5 Zimmer laden um die Ansicht nicht zu überladen
    const sampleRooms = rooms.slice(0, 5);
    const results = await Promise.allSettled(sampleRooms.map((r) =>
      api(`/kkh/api/web/rooms/${encodeURIComponent(r.id)}`).catch(() => null)));
    results.forEach((res, idx) => {
      if (res.status === 'fulfilled' && res.value?.files) {
        const room = sampleRooms[idx];
        // Signaturen werden clientseitig ebenfalls nicht angezeigt
        res.value.files
          .filter((f) => !f.path.includes('_signaturen'))
          .forEach((f) => allFiles.push({ ...f, station: room.station, zimmer: room.zimmer, roomId: room.id }));
      }
    });

    // Fotos (JPEG/PNG, ohne Signaturen)
    const fotos = allFiles.filter((f) => /\.(jpe?g|png)$/i.test(f.path));
    const pdfs = allFiles.filter((f) => f.path.endsWith('.pdf'));

    // Fotos-Galerie
    const fotoPanel = document.getElementById('df-fotos-panel');
    if (fotoPanel) {
      if (fotos.length === 0) {
        fotoPanel.innerHTML = `
          <div class="alert alert-info">
            <strong>Keine Prüfungsfotos in der Stichprobe.</strong><br>
            Fotos werden über die App hochgeladen. Der ZIP-Download oben enthält alle Fotos des gewählten Zeitraums.
          </div>`;
      } else {
        fotoPanel.innerHTML = `
          <p style="font-size:12px;color:var(--muted);margin-bottom:12px">
            ${fotos.length} Foto(s) (Stichprobe – für alle Fotos ZIP-Download nutzen)
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
            Keine gespeicherten PDFs in der Stichprobe. Prüfberichte können jederzeit über den
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
