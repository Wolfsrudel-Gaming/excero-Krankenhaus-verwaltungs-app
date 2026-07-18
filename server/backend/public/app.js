/**
 * KKH TV-Wartung – Shell, Router, Auth
 * Globale Hilfsfunktionen: api(), route(), toast(), modal() kommen aus ui.js
 */

const ROUTES = {
  'dashboard':            viewDashboard,
  'zimmer':               viewZimmer,
  'pruefungen':           viewPruefungen,
  'stundenzettel':        viewStundenzettel,
  'mitarbeiter':          viewMitarbeiter,
  'dateien':              viewDateien,
  'lager-artikel':        viewLagerArtikel,
  'lager-buchungen':      viewLagerBuchungen,
  'lager-verbrauch':      viewLagerVerbrauch,
  'lager-nachbestellung': viewLagerNachbestellung,
  'lieferanten':          viewLieferanten,
  'abrechnung':           viewAbrechnung,
  'benutzer':             viewBenutzer,
};

// API-Wrapper für /kkh Pfad
window.api = function api(url, opts = {}) {
  const fullUrl = url.startsWith('/kkh') ? url : `/kkh${url.startsWith('/') ? '' : '/'}${url}`;
  return (async () => {
    const res = await fetch(fullUrl, {
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
  })();
};

let currentUser = null;

function setzeAktiveNav(route) {
  document.querySelectorAll('.nav-item').forEach((el) => {
    el.classList.toggle('aktiv', el.dataset.route === route);
  });
}

async function route(r) {
  const fn = ROUTES[r];
  if (!fn) { route('dashboard'); return; }
  setzeAktiveNav(r);
  window.location.hash = r;
  // Mobile: Sidebar schließen nach Navigation
  closeSidebar();
  const content = document.getElementById('content-area');
  content.innerHTML = '<div class="loading">Wird geladen…</div>';
  try { await fn(); } catch (e) { console.error(e); toast(e.message || 'Fehler', 'err'); }
}

// Mobile Sidebar
function closeSidebar() {
  document.getElementById('sidebar')?.classList.remove('open');
}
function initMobileNav() {
  const hamburger = document.getElementById('hamburger');
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('sidebar-overlay');
  hamburger?.addEventListener('click', () => sidebar.classList.toggle('open'));
  overlay?.addEventListener('click', closeSidebar);
  // Swipe zum Schließen
  let touchStartX = 0;
  sidebar?.addEventListener('touchstart', (e) => { touchStartX = e.touches[0].clientX; }, { passive: true });
  sidebar?.addEventListener('touchend', (e) => {
    if (e.changedTouches[0].clientX - touchStartX < -50) closeSidebar();
  }, { passive: true });
}

function initSidebar() {
  document.querySelectorAll('.nav-item[data-route]').forEach((el) => {
    el.addEventListener('click', () => route(el.dataset.route));
  });
}

async function checkAuth() {
  try {
    const d = await fetch('/kkh/api/web/me').then((r) => r.ok ? r.json() : null);
    return d?.username || null;
  } catch { return null; }
}

async function doLogin() {
  const username = document.getElementById('l-user').value.trim();
  const password = document.getElementById('l-pw').value;
  const errEl = document.getElementById('l-err');
  const btn = document.getElementById('l-btn');
  errEl.hidden = true;
  if (!username || !password) { errEl.textContent = 'Benutzername und Passwort eingeben.'; errEl.hidden = false; return; }
  btn.disabled = true; btn.textContent = 'Anmelden…';
  try {
    const r = await fetch('/kkh/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    if (r.ok) {
      const d = await r.json();
      currentUser = d.username || username;
      showApp();
    } else {
      const d = await r.json().catch(() => ({}));
      errEl.textContent = d.error || 'Anmeldung fehlgeschlagen.';
      errEl.hidden = false;
    }
  } catch (e) { errEl.textContent = e.message; errEl.hidden = false; }
  finally { btn.disabled = false; btn.textContent = 'Anmelden'; }
}

function showApp() {
  document.getElementById('login-view').hidden = true;
  document.getElementById('app-shell').hidden = false;
  document.getElementById('user-chip').textContent = currentUser ? `👤 ${currentUser}` : '';
  initSidebar();
  initMobileNav();
  const hash = window.location.hash.replace('#', '') || 'dashboard';
  route(ROUTES[hash] ? hash : 'dashboard');
  ladeNachbestellungsBadge();
}

function ladeNachbestellungsBadge() {
  api('/kkh/api/web/lager/nachbestellung').then((d) => {
    const cnt = (d?.artikel || []).length;
    const el = document.getElementById('nb-badge');
    if (el) { el.hidden = cnt === 0; el.textContent = cnt > 0 ? cnt : ''; }
  }).catch(() => {});
}

function showLogin() {
  document.getElementById('login-view').hidden = false;
  document.getElementById('app-shell').hidden = true;
  document.getElementById('l-pw').value = '';
  document.getElementById('l-err').hidden = true;
  document.getElementById('l-user').focus();
  currentUser = null;
}

async function doLogout() {
  await fetch('/kkh/api/logout', { method: 'POST' }).catch(() => {});
  showLogin();
}

async function doChangePw() {
  const res = await modal('Passwort ändern', `
    <div class="form-grid">
      <div class="form-group full"><label class="form-label">Aktuelles Passwort</label>
        <input type="password" class="form-control" id="cp-alt"></div>
      <div class="form-group"><label class="form-label">Neues Passwort</label>
        <input type="password" class="form-control" id="cp-neu"></div>
      <div class="form-group"><label class="form-label">Wiederholen</label>
        <input type="password" class="form-control" id="cp-neu2"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (!res || res.action !== 'ok') return;
  const alt = mf(res, 'cp-alt');
  const neu = mf(res, 'cp-neu');
  const neu2 = mf(res, 'cp-neu2');
  if (!neu || neu.length < 4) { toast('Neues Passwort zu kurz (min. 4 Zeichen)', 'err'); return; }
  if (neu !== neu2) { toast('Passwörter stimmen nicht überein', 'err'); return; }
  try {
    await api('/kkh/api/web/me/password', { method: 'PATCH', body: { oldPassword: alt, newPassword: neu } });
    toast('Passwort erfolgreich geändert');
  } catch (e) { toast(e.message, 'err'); }
}

async function init() {
  document.getElementById('l-btn').addEventListener('click', doLogin);
  document.getElementById('l-pw').addEventListener('keydown', (e) => { if (e.key === 'Enter') doLogin(); });
  document.getElementById('l-user').addEventListener('keydown', (e) => { if (e.key === 'Enter') document.getElementById('l-pw').focus(); });
  document.getElementById('logout-btn').addEventListener('click', doLogout);
  document.getElementById('pw-btn').addEventListener('click', doChangePw);
  window.addEventListener('session-expired', () => { toast('Sitzung abgelaufen – bitte neu anmelden', 'warn'); showLogin(); });
  window.addEventListener('hashchange', () => {
    const r = window.location.hash.replace('#', '');
    if (ROUTES[r] && r !== (window._currentRoute || '')) route(r);
  });

  const user = await checkAuth();
  if (user) { currentUser = user; showApp(); }
  else showLogin();
}

init();
