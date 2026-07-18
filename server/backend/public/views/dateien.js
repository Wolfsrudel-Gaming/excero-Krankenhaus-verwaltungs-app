/* global toast, api */
'use strict';

async function viewDateien() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = `
    <div class="view-header">
      <h1>Dateien &amp; Fotos</h1>
    </div>
    <div class="filter-bar">
      <div class="filter-group"><label>Station</label>
        <input class="form-control" id="df-station" placeholder="z.B. A4"></div>
      <div class="filter-group"><label>Zimmer</label>
        <input class="form-control" id="df-zimmer" placeholder="z.B. 01a"></div>
      <div class="filter-group"><label>Von</label>
        <input type="date" class="form-control" id="df-von"></div>
      <div class="filter-group"><label>Bis</label>
        <input type="date" class="form-control" id="df-bis"></div>
    </div>
    <div style="margin:16px 0;display:flex;gap:10px">
      <button class="btn btn-primary btn-sm" id="zip-dl-btn">📦 Fotos als ZIP herunterladen</button>
      <button class="btn btn-secondary btn-sm" id="zip-hd-btn">☁️ ZIP direkt zu HiDrive</button>
    </div>
    <p class="form-hint">Das ZIP enthält alle Fotos aus Zimmerprüfungen in der Struktur <code>&lt;Station_Zimmer&gt;/&lt;Datum&gt;/Foto.jpg</code></p>
    <hr style="margin:20px 0">
    <h3>Signaturen</h3>
    <p class="form-hint">Unterschriften werden von der App unter <code>_signaturen/&lt;Station&gt;_&lt;Datum&gt;_&lt;rolle&gt;.png</code> hochgeladen und automatisch in Server-seitig generierten PDFs eingebettet.</p>`;

  document.getElementById('zip-dl-btn').addEventListener('click', () => {
    const p = new URLSearchParams();
    const st = document.getElementById('df-station')?.value;
    const zi = document.getElementById('df-zimmer')?.value;
    const vo = document.getElementById('df-von')?.value;
    const bi = document.getElementById('df-bis')?.value;
    if (st) p.set('station', st);
    if (zi) p.set('zimmer', zi);
    if (vo) p.set('von', vo);
    if (bi) p.set('bis', bi);
    window.open(`/kkh/api/web/export/fotos.zip?${p}`, '_blank');
  });

  document.getElementById('zip-hd-btn').addEventListener('click', async () => {
    toast('HiDrive-Upload: Fotos zuerst als ZIP herunterladen, dann manuell über den HiDrive-Browser hochladen.', 'warn');
  });
}
