/* global toast, api */
'use strict';

let hdPfad = '/';
const hdBreadcrumb = [];

async function viewHiDrive() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = `
    <div class="view-header">
      <h1>HiDrive Browser</h1>
    </div>
    <div id="hd-container">
      <div class="loading">Lade…</div>
    </div>`;
  hdPfad = '/';
  hdBreadcrumb.length = 0;
  await hdRender('/');
}

async function hdRender(pfad) {
  const cont = document.getElementById('hd-container');
  cont.innerHTML = '<div class="loading">Lade…</div>';

  let d;
  try {
    d = await api(`/excero/api/web/hidrive/list?pfad=${encodeURIComponent(pfad)}`);
  } catch (e) {
    cont.innerHTML = `<div class="empty-hint">
      <p><b>HiDrive nicht erreichbar:</b> ${e.message}</p>
      <p>Bitte Zugangsdaten unter <a href="#einstellungen" onclick="route('einstellungen')">Einstellungen → HiDrive</a> hinterlegen.</p>
    </div>`;
    return;
  }

  if (d.error) {
    if (d.hinweis) {
      cont.innerHTML = `<div class="empty-hint"><p>${d.error}</p><p>${d.hinweis}</p>
        <button class="btn btn-primary btn-sm" onclick="route('einstellungen')">Zu den Einstellungen</button></div>`;
    } else {
      cont.innerHTML = `<div class="empty-hint">${d.error}</div>`;
    }
    return;
  }

  const eintraege = d.eintraege || [];
  const breadcrumb = pfad.split('/').filter(Boolean);

  cont.innerHTML = `
    <div class="hd-toolbar">
      <div class="hd-breadcrumb">
        <span class="hd-bc-item" data-pfad="/" style="cursor:pointer">🏠</span>
        ${breadcrumb.map((t, i) => {
          const p = '/' + breadcrumb.slice(0, i + 1).join('/');
          return `<span class="hd-bc-sep">/</span><span class="hd-bc-item" data-pfad="${p}" style="cursor:pointer">${t}</span>`;
        }).join('')}
      </div>
      <div style="display:flex;gap:8px">
        <button class="btn btn-secondary btn-sm" id="hd-upload-btn">⬆️ Hochladen</button>
        <input type="file" id="hd-file-input" hidden>
      </div>
    </div>
    <table class="data-table" style="width:100%">
      <thead><tr><th>Name</th><th style="width:100px">Größe</th><th style="width:180px">Geändert</th><th style="width:100px"></th></tr></thead>
      <tbody>
        ${eintraege.length === 0 ? '<tr><td colspan="4" style="color:#999;text-align:center;padding:12px">Ordner leer</td></tr>' : ''}
        ${eintraege.map((e) => `
          <tr>
            <td>
              ${e.isOrdner
                ? `<span class="hd-ordner" data-pfad="${e.href}" style="cursor:pointer">📁 ${e.name}</span>`
                : `📄 ${e.name}`}
            </td>
            <td style="text-align:right;color:#888">${e.isOrdner ? '–' : fmtGroesse(e.groesse)}</td>
            <td style="color:#888">${e.geaendert}</td>
            <td style="text-align:right">
              ${!e.isOrdner ? `<a href="/excero/api/web/hidrive/download?pfad=${encodeURIComponent(e.href)}" class="btn btn-ghost btn-xs" download>⬇️</a>` : ''}
              <button class="btn btn-ghost btn-xs hd-del" data-pfad="${e.href}" data-name="${e.name}">✕</button>
            </td>
          </tr>`).join('')}
      </tbody>
    </table>`;

  // Breadcrumb
  cont.querySelectorAll('.hd-bc-item').forEach((el) => {
    el.addEventListener('click', () => hdRender(el.dataset.pfad));
  });
  // Ordner öffnen
  cont.querySelectorAll('.hd-ordner').forEach((el) => {
    el.addEventListener('click', () => hdRender(el.dataset.pfad));
  });
  // Löschen
  cont.querySelectorAll('.hd-del').forEach((el) => {
    el.addEventListener('click', async () => {
      if (!confirm(`"${el.dataset.name}" löschen?`)) return;
      try {
        await api(`/excero/api/web/hidrive/delete?pfad=${encodeURIComponent(el.dataset.pfad)}`, { method: 'DELETE' });
        toast('Gelöscht'); hdRender(pfad);
      } catch (e) { toast(e.message, 'err'); }
    });
  });
  // Upload
  const uploadBtn = cont.querySelector('#hd-upload-btn');
  const fileInput = cont.querySelector('#hd-file-input');
  if (uploadBtn && fileInput) {
    uploadBtn.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async () => {
      const file = fileInput.files[0];
      if (!file) return;
      const zielPfad = pfad.replace(/\/$/, '') + '/' + file.name;
      try {
        const body = await file.arrayBuffer();
        await fetch(`/excero/api/web/hidrive/upload?pfad=${encodeURIComponent(zielPfad)}`, {
          method: 'PUT',
          headers: { 'Content-Type': file.type || 'application/octet-stream' },
          body,
        });
        toast('Hochgeladen');
        hdRender(pfad);
      } catch (e) { toast(e.message, 'err'); }
    });
  }
}

function fmtGroesse(bytes) {
  if (!bytes) return '0';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1048576).toFixed(1)} MB`;
}
