/**
 * Dateien & Fotos – KKH TV-Wartung
 * - Galerie-Ansicht für Fotos (Thumbnails)
 * - Dateiliste für PDFs/Signaturen
 * - ZIP-Download nach Station/Zeitraum
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
      <button class="tab-btn" data-tab="fotos">Fotos</button>
      <button class="tab-btn" data-tab="pdfs">PDFs</button>
      <button class="tab-btn" data-tab="signaturen">Signaturen</button>
    </div>

    <div data-panel="fotos" id="df-fotos-panel">
      <div class="loading">Fotos werden geladen…</div>
    </div>
    <div data-panel="pdfs" id="df-pdfs-panel" hidden>
      <div class="loading">PDFs werden geladen…</div>
    </div>
    <div data-panel="signaturen" id="df-sig-panel" hidden>
      <div class="loading">Signaturen werden geladen…</div>
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
    // Lade Dateien für alle Zimmer parallel (batch von max. 20 gleichzeitig)
    const batchSize = 20;
    for (let i = 0; i < Math.min(rooms.length, 5); i += batchSize) {
      const batch = rooms.slice(i, i + batchSize);
      const results = await Promise.allSettled(batch.map((r) =>
        api(`/kkh/api/web/rooms/${encodeURIComponent(r.id)}`).catch(() => null)));
      results.forEach((res, idx) => {
        if (res.status === 'fulfilled' && res.value?.files) {
          const room = batch[idx];
          res.value.files.forEach((f) => allFiles.push({ ...f, station: room.station, zimmer: room.zimmer, roomId: room.id }));
        }
      });
    }

    // Fotos (JPEG/PNG)
    const fotos = allFiles.filter((f) => /\.(jpe?g|png)$/i.test(f.path));
    const pdfs = allFiles.filter((f) => f.path.endsWith('.pdf'));
    const sigs = allFiles.filter((f) => f.path.includes('_signaturen'));

    // Fotos-Galerie
    const fotoPanel = document.getElementById('df-fotos-panel');
    if (fotoPanel) {
      if (fotos.length === 0) {
        fotoPanel.innerHTML = '<div class="alert alert-info">Keine Fotos vorhanden. Fotos werden über die App hochgeladen.</div>';
      } else {
        fotoPanel.innerHTML = `<p style="font-size:12px;color:var(--muted);margin-bottom:12px">${fotos.length} Foto(s) gefunden</p>
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
        pdfPanel.innerHTML = '<div class="alert alert-info">Keine PDFs vorhanden. Prüfberichte werden servergesteuert erzeugt.</div>';
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

    // Signaturen
    const sigPanel = document.getElementById('df-sig-panel');
    if (sigPanel) {
      if (sigs.length === 0) {
        sigPanel.innerHTML = '<div class="alert alert-info">Keine Signaturen vorhanden. Signaturen werden über die App hochgeladen und für PDF-Generierung verwendet.</div>';
      } else {
        sigPanel.innerHTML = `<p style="font-size:12px;color:var(--muted);margin-bottom:12px">${sigs.length} Signatur(en) vorhanden</p>
          <div class="foto-grid">
            ${sigs.map((f) => `
              <div class="foto-thumb">
                <a href="/kkh/api/web/file?path=${encodeURIComponent(f.path)}" target="_blank">
                  <img src="/kkh/api/web/thumb?path=${encodeURIComponent(f.path)}" alt="${escH(f.path.split('/').pop())}" loading="lazy" style="object-fit:contain;background:#fff;padding:8px">
                </a>
                <div class="foto-name">${escH(f.path.split('/').pop())}</div>
              </div>`).join('')}
          </div>`;
      }
    }
  }

  document.getElementById('df-reload')?.addEventListener('click', ladeDateien);
  await ladeDateien();
}
