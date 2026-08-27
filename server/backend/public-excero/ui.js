/**
 * ui.js – Wiederverwendbare UI-Helpers:
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
    setTimeout(() => t.remove(), 250);
  }, typ === 'err' ? 5000 : 3000);
}

/* ── Modal ──────────────────────────────────────────────────────────── */
function modal(title, bodyHtml, buttons = []) {
  return new Promise((resolve) => {
    const bg = document.createElement('div');
    bg.className = 'modal-bg';
    bg.innerHTML = `
      <div class="modal" role="dialog" aria-modal="true">
        <div class="modal-title">${escH(title)}</div>
        <div class="modal-body">${bodyHtml}</div>
        <div class="modal-actions">${
          buttons.map((b, i) =>
            `<button class="btn ${b.cls || 'btn-secondary'}" data-i="${i}">${escH(b.label)}</button>`
          ).join('')
        }</div>
      </div>`;
    document.body.appendChild(bg);
    bg.addEventListener('click', (e) => {
      const btn = e.target.closest('[data-i]');
      if (btn) { bg.remove(); resolve(buttons[Number(btn.dataset.i)]?.value); }
      if (e.target === bg) { bg.remove(); resolve(null); }
    });
    bg.querySelector('.modal-body')?.querySelector('input, select')?.focus();
  });
}

function confirm(msg) {
  return modal('Bestätigung', `<p>${escH(msg)}</p>`, [
    { label: 'Abbrechen', cls: 'btn-secondary', value: false },
    { label: 'OK', cls: 'btn-danger', value: true },
  ]);
}

/* ── Utility ────────────────────────────────────────────────────────── */
function escH(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function fmt(v, type = 'text') {
  if (v === null || v === undefined || v === '') return '–';
  if (type === 'eur') return Number(v).toLocaleString('de-DE', { style: 'currency', currency: 'EUR' });
  if (type === 'num') return Number(v).toLocaleString('de-DE', { maximumFractionDigits: 2 });
  if (type === 'bool') return v ? '✔' : '–';
  return escH(String(v));
}

function heute() { return new Date().toISOString().slice(0, 10); }
function vor30T() { return new Date(Date.now() - 30 * 86400e3).toISOString().slice(0, 10); }

/* ── Export-Links ───────────────────────────────────────────────────── */
function exportBtn(modul, qs = '') {
  const base = `/kkh/api/web/export/${modul}`;
  const q = qs ? `?${qs}` : '';
  return `
    <a href="${base}.xlsx${q}" class="btn btn-secondary btn-sm">⬇ XLSX</a>
    <a href="${base}.csv${q}" class="btn btn-secondary btn-sm">⬇ CSV</a>`;
}

/* ── DataGrid ───────────────────────────────────────────────────────── */
/**
 * Erzeugt eine sortierbare/filterbare Datentabelle.
 * @param {object} opts
 *   columns: [{key, label, cls?, numFmt?, render?}]
 *   rows: array of objects
 *   summary?: {label, values: {key: val}}  -> tfoot
 *   rowClick?: fn(row)
 *   actions?: fn(row) -> html-string
 */
function DataGrid(containerId, opts) {
  const el = document.getElementById(containerId);
  if (!el) return;
  let sortKey = null, sortDir = 1;
  let filterVal = '';
  let data = opts.rows || [];

  function sortedFiltered() {
    let rows = data;
    if (filterVal) {
      const q = filterVal.toLowerCase();
      rows = rows.filter((r) => opts.columns.some((c) => String(r[c.key] ?? '').toLowerCase().includes(q)));
    }
    if (sortKey) {
      rows = [...rows].sort((a, b) => {
        const av = a[sortKey] ?? '', bv = b[sortKey] ?? '';
        const cmp = typeof av === 'number' && typeof bv === 'number'
          ? av - bv : String(av).localeCompare(String(bv), 'de', { numeric: true });
        return cmp * sortDir;
      });
    }
    return rows;
  }

  function render() {
    const rows = sortedFiltered();
    const thead = opts.columns.map((c, ci) => {
      let cls = c.cls || '';
      if (c.num) cls += ' num';
      const s = sortKey === c.key ? (sortDir === 1 ? ' sort-asc' : ' sort-desc') : '';
      return `<th class="${cls}${s}" data-ci="${ci}">${escH(c.label)}</th>`;
    }).join('');
    const actCol = opts.actions ? '<th class="no-sort">Aktionen</th>' : '';

    const tbody = rows.length === 0
      ? `<tr><td colspan="${opts.columns.length + (opts.actions ? 1 : 0)}" class="no-data">Keine Einträge gefunden.</td></tr>`
      : rows.map((r) => {
          const tds = opts.columns.map((c) => {
            let val = c.render ? c.render(r, c) : escH(r[c.key] ?? '');
            if (!c.render && c.numFmt === 'eur') val = fmt(r[c.key], 'eur');
            if (!c.render && c.numFmt === 'num') val = fmt(r[c.key], 'num');
            const cls = (c.num ? ' num' : '') + (c.cls ? ` ${c.cls}` : '');
            return `<td class="${cls}">${val}</td>`;
          }).join('');
          const actTd = opts.actions ? `<td class="akts">${opts.actions(r)}</td>` : '';
          const rowCls = (opts.rowClass ? opts.rowClass(r) : '') + (opts.rowClick ? ' klickbar' : '');
          return `<tr class="${rowCls}" data-id="${escH(String(r.id ?? r._key ?? ''))}">${tds}${actTd}</tr>`;
        }).join('');

    let tfoot = '';
    if (opts.summary) {
      const tds = opts.columns.map((c) => {
        const v = opts.summary.values[c.key];
        return `<td class="${c.num ? 'num' : ''}">${v !== undefined ? escH(String(v)) : ''}</td>`;
      }).join('');
      const actTd = opts.actions ? '<td></td>' : '';
      tfoot = `<tfoot><tr><td><strong>${escH(opts.summary.label)}</strong></td>${tds.slice(tds.indexOf('</td>'))}</tr></tfoot>`;
    }

    el.innerHTML = `
      <table class="data-grid">
        <thead><tr>${thead}${actCol}</tr></thead>
        <tbody>${tbody}</tbody>
        ${tfoot}
      </table>`;

    el.querySelectorAll('thead th[data-ci]').forEach((th) => {
      th.addEventListener('click', () => {
        const ci = Number(th.dataset.ci);
        const col = opts.columns[ci];
        if (!col) return;
        if (sortKey === col.key) sortDir = -sortDir;
        else { sortKey = col.key; sortDir = 1; }
        render();
      });
    });
    if (opts.rowClick) {
      el.querySelectorAll('tbody tr.klickbar').forEach((tr) => {
        tr.addEventListener('click', (e) => {
          if (e.target.closest('.akts, button, a')) return;
          const id = tr.dataset.id;
          const row = rows.find((r) => String(r.id ?? r._key ?? '') === id);
          if (row) opts.rowClick(row);
        });
      });
    }
  }

  this.update = (newData) => { data = newData; render(); };
  this.filter = (val) => { filterVal = val; render(); };
  render();
}

/* ── Tabwechsel-Helper ──────────────────────────────────────────────── */
function initTabs(containerId) {
  const wrap = document.getElementById(containerId);
  if (!wrap) return;
  wrap.querySelectorAll('.tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      wrap.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('aktiv'));
      btn.classList.add('aktiv');
      const target = btn.dataset.tab;
      wrap.querySelectorAll('.tab-pane').forEach((p) => p.hidden = p.id !== target);
    });
  });
  const first = wrap.querySelector('.tab-btn');
  if (first) first.click();
}
