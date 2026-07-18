/**
 * ui.js – Wiederverwendbare UI-Helpers für die KKH Webapp
 * api(), toast(), modal(), DataGrid, formatDate, etc.
 */

/* ── API-Helper ─────────────────────────────────────────────────────── */
async function api(url, opts = {}) {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) },
    ...opts,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 401) { window.dispatchEvent(new Event('session-expired')); return null; }
  const ct = res.headers.get('content-type') || '';
  if (!ct.includes('application/json')) return res;
  const j = await res.json();
  if (!res.ok) throw new Error(j.error || `HTTP ${res.status}`);
  return j;
}

/* ── Toast ──────────────────────────────────────────────────────────── */
function toast(msg, typ = 'ok') {
  const wrap = document.getElementById('toast-wrap');
  const t = document.createElement('div');
  t.className = `toast${typ === 'err' ? ' err' : typ === 'warn' ? ' warn' : ''}`;
  t.textContent = msg;
  wrap.appendChild(t);
  requestAnimationFrame(() => { requestAnimationFrame(() => t.classList.add('show')); });
  setTimeout(() => {
    t.classList.remove('show');
    setTimeout(() => t.remove(), 300);
  }, typ === 'err' ? 5000 : 3000);
}

/* ── Modal ───────────────────────────────────────────────────────────
   WICHTIG: Formularwerte werden VOR dem Schließen des Modals gesammelt
   und als Objekt an den Resolver übergeben. So kein DOM-nach-remove-Bug.
*/
function modal(title, body, buttons = [{ label: 'OK', cls: 'btn-primary', value: 'ok' }]) {
  return new Promise((resolve) => {
    const bg = document.createElement('div');
    bg.className = 'modal-bg';
    bg.innerHTML = `
      <div class="modal-box" role="dialog" aria-modal="true" aria-label="${escH(title)}">
        <div class="modal-header">
          <h3 class="modal-title">${escH(title)}</h3>
          <button class="modal-close" aria-label="Schließen">✕</button>
        </div>
        <div class="modal-body">${body}</div>
        <div class="modal-footer">
          ${buttons.map((b, i) =>
            `<button class="btn ${b.cls || 'btn-secondary'}" data-idx="${i}">${escH(b.label)}</button>`
          ).join('')}
        </div>
      </div>`;
    document.body.appendChild(bg);
    requestAnimationFrame(() => bg.classList.add('open'));

    // Ersten Input fokussieren
    setTimeout(() => bg.querySelector('input,select,textarea')?.focus(), 50);

    function close(value) {
      bg.classList.remove('open');
      setTimeout(() => bg.remove(), 200);
      resolve(value);
    }

    bg.querySelector('.modal-close').addEventListener('click', () => close(null));
    bg.addEventListener('click', (e) => { if (e.target === bg) close(null); });
    bg.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(null); });

    bg.querySelector('.modal-footer').addEventListener('click', (e) => {
      const btn = e.target.closest('button[data-idx]');
      if (!btn) return;
      const idx = Number(btn.dataset.idx);
      const cfg = buttons[idx];
      // Werte VOR dem Schließen sammeln
      const fields = {};
      bg.querySelectorAll('[id]').forEach((el) => {
        if (el.tagName === 'INPUT' || el.tagName === 'SELECT' || el.tagName === 'TEXTAREA') {
          fields[el.id] = el.type === 'checkbox' ? el.checked : el.value;
        }
      });
      close(cfg.value === null ? null : { action: cfg.value, fields });
    });
  });
}

// Hilfsfunktion: Felder aus Modal-Result holen
function mf(result, id) {
  if (!result || !result.fields) return undefined;
  return result.fields[id];
}

/* ── Bestätigung ─────────────────────────────────────────────────────── */
function confirm(text) {
  return new Promise((resolve) => {
    const bg = document.createElement('div');
    bg.className = 'modal-bg';
    bg.innerHTML = `
      <div class="modal-box modal-box-sm" role="alertdialog">
        <div class="modal-body" style="padding:28px 24px 8px">${escH(text)}</div>
        <div class="modal-footer">
          <button class="btn btn-secondary" id="cf-no">Abbrechen</button>
          <button class="btn btn-danger" id="cf-yes">Bestätigen</button>
        </div>
      </div>`;
    document.body.appendChild(bg);
    requestAnimationFrame(() => bg.classList.add('open'));
    function close(v) { bg.classList.remove('open'); setTimeout(() => bg.remove(), 200); resolve(v); }
    bg.querySelector('#cf-yes').addEventListener('click', () => close(true));
    bg.querySelector('#cf-no').addEventListener('click', () => close(false));
    bg.addEventListener('click', (e) => { if (e.target === bg) close(false); });
    bg.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(false); if (e.key === 'Enter') close(true); });
    bg.querySelector('#cf-yes').focus();
  });
}

/* ── DataGrid ────────────────────────────────────────────────────────── */
class DataGrid {
  constructor(container, opts) {
    this.container = typeof container === 'string' ? document.querySelector(container) : container;
    this.opts = { filterKeys: [], defaultSort: null, pageSize: 200, ...opts };
    this.data = opts.data || [];
    this.filteredData = [...this.data];
    this.sortKey = null;
    this.sortDir = 1;
    this.page = 0;
    this.render();
  }

  render() {
    const { columns, filterKeys, pageSize } = this.opts;
    this.container.innerHTML = '';

    // Toolbar
    const toolbar = document.createElement('div');
    toolbar.className = 'dg-toolbar';

    if (filterKeys.length) {
      const inp = document.createElement('input');
      inp.className = 'dg-filter';
      inp.placeholder = '🔍  Suchen…';
      inp.setAttribute('aria-label', 'Tabelle filtern');
      inp.addEventListener('input', () => { this.page = 0; this.applyFilter(inp.value); });
      toolbar.appendChild(inp);
    }

    const info = document.createElement('span');
    info.className = 'dg-info';
    toolbar.appendChild(info);
    this.container.appendChild(toolbar);

    // Tabelle
    const wrap = document.createElement('div');
    wrap.className = 'dg-wrap';
    const table = document.createElement('table');
    table.className = 'data-table';
    table.setAttribute('role', 'grid');

    // Head
    const thead = document.createElement('thead');
    const tr = document.createElement('tr');
    for (const col of columns) {
      const th = document.createElement('th');
      th.textContent = col.label || '';
      if (col.width) th.style.width = col.width;
      if (col.align) th.style.textAlign = col.align;
      if (col.sort) {
        th.classList.add('sortable');
        th.setAttribute('tabindex', '0');
        th.setAttribute('role', 'columnheader');
        th.setAttribute('aria-sort', 'none');
        th.addEventListener('click', () => this.sortBy(col.key, th, tr));
        th.addEventListener('keydown', (e) => { if (e.key === 'Enter') this.sortBy(col.key, th, tr); });
      }
      tr.appendChild(th);
    }
    thead.appendChild(tr);
    table.appendChild(thead);

    this.tbody = document.createElement('tbody');
    table.appendChild(this.tbody);
    wrap.appendChild(table);
    this.container.appendChild(wrap);

    // Pagination
    this.paginationEl = document.createElement('div');
    this.paginationEl.className = 'dg-pagination';
    this.container.appendChild(this.paginationEl);

    this.infoEl = info;
    this.tableHeadRow = tr;
    this.renderRows();
  }

  sortBy(key, th, tr) {
    if (this.sortKey === key) this.sortDir *= -1;
    else { this.sortKey = key; this.sortDir = 1; }
    tr.querySelectorAll('th[aria-sort]').forEach((h) => h.setAttribute('aria-sort', 'none'));
    th.setAttribute('aria-sort', this.sortDir === 1 ? 'ascending' : 'descending');
    this.renderRows();
  }

  applyFilter(q) {
    const lower = q.toLowerCase().trim();
    if (!lower) {
      this.filteredData = [...this.data];
    } else {
      this.filteredData = this.data.filter((row) =>
        this.opts.filterKeys.some((k) => String(row[k] ?? '').toLowerCase().includes(lower)));
    }
    this.renderRows();
  }

  update(data) {
    this.data = data;
    const q = this.container.querySelector('.dg-filter')?.value || '';
    this.applyFilter(q);
  }

  renderRows() {
    const { columns, pageSize, onRowClick } = this.opts;
    let rows = [...this.filteredData];

    if (this.sortKey) {
      rows.sort((a, b) => {
        const av = a[this.sortKey] ?? '';
        const bv = b[this.sortKey] ?? '';
        const n = !isNaN(Number(av)) && !isNaN(Number(bv));
        return (n ? Number(av) - Number(bv) : String(av).localeCompare(String(bv), 'de')) * this.sortDir;
      });
    }

    // Pagination
    const total = rows.length;
    const pages = Math.max(1, Math.ceil(total / pageSize));
    this.page = Math.min(this.page, pages - 1);
    const slice = rows.slice(this.page * pageSize, (this.page + 1) * pageSize);

    this.tbody.innerHTML = '';
    if (slice.length === 0) {
      const r = document.createElement('tr');
      r.innerHTML = `<td colspan="${columns.length}" class="dg-empty">Keine Einträge</td>`;
      this.tbody.appendChild(r);
    }

    for (const row of slice) {
      const tr = document.createElement('tr');
      if (onRowClick) { tr.classList.add('clickable'); tr.addEventListener('click', (e) => { if (!e.target.closest('button,a')) onRowClick(row); }); }
      for (const col of columns) {
        const td = document.createElement('td');
        if (col.align) td.style.textAlign = col.align;
        const val = row[col.key];
        td.innerHTML = col.render ? col.render(val, row) : escH(String(val ?? ''));
        tr.appendChild(td);
      }
      if (row._rowClass) tr.className = row._rowClass;
      this.tbody.appendChild(tr);
    }

    // Info + Pagination
    if (this.infoEl) {
      this.infoEl.textContent = total > pageSize
        ? `${slice.length} von ${total} Einträgen (Seite ${this.page + 1}/${pages})`
        : `${total} Einträge`;
    }
    this.paginationEl.innerHTML = '';
    if (pages > 1) {
      for (let i = 0; i < pages; i++) {
        const b = document.createElement('button');
        b.className = `btn btn-sm ${i === this.page ? 'btn-primary' : 'btn-ghost'}`;
        b.textContent = i + 1;
        b.addEventListener('click', () => { this.page = i; this.renderRows(); });
        this.paginationEl.appendChild(b);
      }
    }
  }
}

/* ── Formatierung ─────────────────────────────────────────────────────── */
function escH(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function fmtDatum(s) {
  if (!s) return '–';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(s));
  return m ? `${m[3]}.${m[2]}.${m[1]}` : s;
}

function fmtEur(n) {
  return Number(n || 0).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
}

function fmtNum(n, dec = 2) {
  return Number(n || 0).toLocaleString('de-DE', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function heute() { return new Date().toISOString().slice(0, 10); }
function monatVon() {
  const d = new Date(); d.setDate(1);
  return d.toISOString().slice(0, 10);
}

/* ── Status-Badge ─────────────────────────────────────────────────────── */
function badge(text, typ = '') {
  return `<span class="badge badge-${typ}">${escH(text)}</span>`;
}

function freenetBadge(bis) {
  if (!bis) return badge('kein Vertrag', 'gray');
  const tage = Math.round((new Date(bis) - Date.now()) / 86400e3);
  if (tage < 0) return badge(`Abgelaufen ${fmtDatum(bis)}`, 'err');
  if (tage <= 30) return badge(`Läuft ab ${fmtDatum(bis)}`, 'warn');
  return badge(fmtDatum(bis), 'ok');
}

/* ── Export-Button ─────────────────────────────────────────────────────── */
function exportBtn(url, label = 'Export') {
  return `<a href="${url}" target="_blank" class="btn btn-secondary btn-sm">${escH(label)}</a>`;
}

/* ── Tabs ─────────────────────────────────────────────────────────────── */
function initTabs(container) {
  const btns = container.querySelectorAll('[data-tab]');
  const panels = container.querySelectorAll('[data-panel]');
  btns.forEach((btn) => {
    btn.addEventListener('click', () => {
      btns.forEach((b) => b.classList.remove('active'));
      panels.forEach((p) => p.hidden = true);
      btn.classList.add('active');
      const panel = container.querySelector(`[data-panel="${btn.dataset.tab}"]`);
      if (panel) panel.hidden = false;
    });
  });
  btns[0]?.click();
}

/* ── Page-Skeleton ────────────────────────────────────────────────────── */
function pageHeader(title, actions = '') {
  return `<div class="page-header"><h1 class="page-title">${escH(title)}</h1><div class="page-actions">${actions}</div></div>`;
}

function skeleton() {
  return '<div class="skeleton-wrap"><div class="skeleton"></div><div class="skeleton short"></div><div class="skeleton"></div></div>';
}

/* ── Datum-Picker Pair ────────────────────────────────────────────────── */
function datumFilterBar(vonId, bisId, label = 'Zeitraum', onchange) {
  return `<div class="filter-bar">
    <div class="filter-group"><label>${label} von</label>
      <input type="date" class="form-control" id="${vonId}" value="${monatVon()}"></div>
    <div class="filter-group"><label>bis</label>
      <input type="date" class="form-control" id="${bisId}" value="${heute()}"></div>
    <button class="btn btn-primary btn-sm" id="filter-laden-btn">Laden</button>
  </div>`;
}

window.escH = escH;
window.fmtDatum = fmtDatum;
window.fmtEur = fmtEur;
window.fmtNum = fmtNum;
window.badge = badge;
window.freenetBadge = freenetBadge;
window.mf = mf;
