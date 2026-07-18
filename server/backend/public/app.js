/**
 * Excero Webapp – Shell, Router, Auth, Mandanten-Umschalter
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
  'rechnungen':           viewRechnungen,
  'ausgaben':             viewAusgaben,
  'guv':                  viewGuv,
  'baustellen':           viewBaustellen,
  'zeiterfassung':        viewZeiterfassung,
  'hidrive':              viewHiDrive,
  'einstellungen':        viewEinstellungen,
  'firmen':               viewFirmen,
  'benutzer':             viewBenutzer,
};

let currentUser = null;
// Firma: Excero GmbH (einzige Firma, wird beim Start geladen)
window.aktiveFirmaId = null;
window.aktiveFirmaName = '';

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
  try { await fn(); } catch (e) { console.error(e); toast(e.message || 'Fehler', 'err'); }
}

function initSidebar() {
  document.querySelectorAll('.nav-item[data-route]').forEach((el) => {
    el.addEventListener('click', () => route(el.dataset.route));
  });
}

async function ladeFirmen() {
  try {
    const d = await api('/kkh/api/web/firmen');
    const firmen = d.firmen || [];
    if (firmen.length > 0) {
      window.aktiveFirmaId = firmen[0].id;
      window.aktiveFirmaName = firmen[0].name;
      document.getElementById('topbar-sub').textContent = firmen[0].name;
    }
  } catch {}
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
  errEl.hidden = true;
  if (!username || !password) { errEl.textContent = 'Benutzername und Passwort eingeben.'; errEl.hidden = false; return; }
  try {
    const r = await fetch('/kkh/api/login', {
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
  await ladeFirmen();
  const hash = window.location.hash.replace('#', '') || 'dashboard';
  route(ROUTES[hash] ? hash : 'dashboard');
  // Nachbestellungs-Badge laden
  try {
    const d = await api('/kkh/api/web/lager/nachbestellung');
    const cnt = (d.artikel || []).length;
    const badge = document.getElementById('nb-badge');
    if (badge) { badge.hidden = cnt === 0; badge.textContent = cnt > 0 ? cnt : ''; }
  } catch {}
}

function showLogin() {
  document.getElementById('login-view').hidden = false;
  document.getElementById('app-shell').hidden = true;
  document.getElementById('l-pw').value = '';
  document.getElementById('l-err').hidden = true;
  currentUser = null;
}

async function doLogout() {
  await fetch('/kkh/api/logout', { method: 'POST' }).catch(() => {});
  showLogin();
}

async function doChangePw() {
  const res = await modal('Passwort ändern', `
    <div class="form-grid">
      <div class="form-group"><label class="form-label">Aktuelles Passwort</label>
        <input type="password" class="form-control" id="cp-alt"></div>
      <div class="form-group"><label class="form-label">Neues Passwort</label>
        <input type="password" class="form-control" id="cp-neu"></div>
      <div class="form-group"><label class="form-label">Wiederholen</label>
        <input type="password" class="form-control" id="cp-neu2"></div>
    </div>`,
    [{ label: 'Abbrechen', value: null }, { label: 'Speichern', cls: 'btn-primary', value: 'ok' }]);
  if (res !== 'ok') return;
  const alt = document.getElementById('cp-alt')?.value;
  const neu = document.getElementById('cp-neu')?.value;
  const neu2 = document.getElementById('cp-neu2')?.value;
  if (neu !== neu2) { toast('Passwörter stimmen nicht überein', 'err'); return; }
  try {
    await api('/kkh/api/web/me/password', { method: 'PATCH', body: { oldPassword: alt, newPassword: neu }});
    toast('Passwort geändert');
  } catch (e) { toast(e.message, 'err'); }
}

async function init() {
  document.getElementById('l-btn').addEventListener('click', doLogin);
  document.getElementById('l-pw').addEventListener('keydown', (e) => { if (e.key === 'Enter') doLogin(); });
  document.getElementById('l-user').addEventListener('keydown', (e) => { if (e.key === 'Enter') document.getElementById('l-pw').focus(); });
  document.getElementById('logout-btn').addEventListener('click', doLogout);
  document.getElementById('pw-btn').addEventListener('click', doChangePw);
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
