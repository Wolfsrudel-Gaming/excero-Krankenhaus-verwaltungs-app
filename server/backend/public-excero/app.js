/**
 * Excero Webapp – Shell, Router, Auth
 * Läuft unter /excero/ – API unter /excero/api/
 */

const BASE = '/excero';

const ROUTES = {
  'rechnungen':   viewRechnungen,
  'kunden':       viewKunden,
  'ausgaben':     viewAusgaben,
  'guv':          viewGuv,
  'baustellen':   viewBaustellen,
  'zeiterfassung':viewZeiterfassung,
  'hidrive':      viewHiDrive,
  'einstellungen':viewEinstellungen,
  'firma':        viewFirma,
};

let currentUser = null;
window.aktiveFirmaId = null;

function setzeAktiveNav(route) {
  document.querySelectorAll('.nav-item').forEach((el) => {
    el.classList.toggle('aktiv', el.dataset.route === route);
  });
}

async function route(r) {
  const fn = ROUTES[r];
  if (!fn) { route('rechnungen'); return; }
  setzeAktiveNav(r);
  window.location.hash = r;
  try { await fn(); } catch (e) { console.error(e); toast(e.message || 'Fehler', 'err'); }
}

function initSidebar() {
  document.querySelectorAll('.nav-item[data-route]').forEach((el) => {
    el.addEventListener('click', () => route(el.dataset.route));
  });
}

// Originale api()-Funktion überschreiben: Basispfad /excero einfügen
const _origApi = api;
window.api = function(url, opts) {
  // Relative Pfade bekommen /excero vorangestellt
  if (url.startsWith('/excero/') || url.startsWith('http')) return _origApi(url, opts);
  if (url.startsWith('/kkh/')) return _origApi(url, opts);
  // /api/... → /excero/api/...
  return _origApi(url.replace(/^\//, `${BASE}/`), opts);
};

async function checkAuth() {
  try {
    const d = await fetch(`${BASE}/api/me`).then((r) => r.ok ? r.json() : null);
    return d?.username || null;
  } catch { return null; }
}

async function ladeFirma() {
  try {
    const d = await api(`${BASE}/api/web/firmen`);
    const f = (d.firmen || [])[0];
    if (f) {
      window.aktiveFirmaId = f.id;
      document.getElementById('topbar-firma').textContent = f.name;
    }
  } catch {}
}

async function doLogin() {
  const username = document.getElementById('l-user').value.trim();
  const password = document.getElementById('l-pw').value;
  const errEl = document.getElementById('l-err');
  errEl.hidden = true;
  if (!username || !password) { errEl.textContent = 'Benutzername und Passwort eingeben.'; errEl.hidden = false; return; }
  try {
    const r = await fetch(`${BASE}/api/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    if (r.ok) {
      const d = await r.json();
      currentUser = d.username || username;
      await showApp();
    } else {
      const d = await r.json().catch(() => ({}));
      errEl.textContent = d.error || 'Anmeldung fehlgeschlagen.';
      errEl.hidden = false;
    }
  } catch (e) { errEl.textContent = e.message; errEl.hidden = false; }
}

async function showApp() {
  document.getElementById('login-view').hidden = true;
  document.getElementById('app-shell').hidden = false;
  document.getElementById('user-chip').textContent = currentUser ? `👤 ${currentUser}` : '';
  initSidebar();
  await ladeFirma();
  const hash = window.location.hash.replace('#', '') || 'rechnungen';
  route(ROUTES[hash] ? hash : 'rechnungen');
}

function showLogin() {
  document.getElementById('login-view').hidden = false;
  document.getElementById('app-shell').hidden = true;
  document.getElementById('l-pw').value = '';
  document.getElementById('l-err').hidden = true;
  currentUser = null;
}

async function doLogout() {
  await fetch(`${BASE}/api/logout`, { method: 'POST' }).catch(() => {});
  showLogin();
}

async function init() {
  document.getElementById('l-btn').addEventListener('click', doLogin);
  document.getElementById('l-pw').addEventListener('keydown', (e) => { if (e.key === 'Enter') doLogin(); });
  document.getElementById('l-user').addEventListener('keydown', (e) => { if (e.key === 'Enter') document.getElementById('l-pw').focus(); });
  document.getElementById('logout-btn').addEventListener('click', doLogout);
  window.addEventListener('session-expired', () => { toast('Sitzung abgelaufen', 'warn'); showLogin(); });
  window.addEventListener('hashchange', () => {
    const r = window.location.hash.replace('#', '');
    if (ROUTES[r]) route(r);
  });

  const user = await checkAuth();
  if (user) { currentUser = user; await showApp(); }
  else showLogin();
}

init();
