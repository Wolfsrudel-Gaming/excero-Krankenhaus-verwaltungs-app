/**
 * Benutzer-View – KKH TV-Wartung
 * - Anlegen / Löschen
 * - Passwort-Reset via PATCH /api/web/users/:id
 * - Umbenennen
 */

async function viewBenutzer() {
  const el = document.getElementById('content-area');
  el.innerHTML = `
    ${pageHeader('Benutzer & Zugänge', `
      <button class="btn btn-primary" id="bu-neu">+ Benutzer anlegen</button>
    `)}
    <div class="settings-section" style="margin-bottom:20px">
      <h3>🔑 App-Zugang (API-Schlüssel)</h3>
      <p class="form-hint">Diesen Schlüssel tragen die Handys unter <em>Einstellungen → Server-Synchronisation → API-Schlüssel</em> ein (zusammen mit der Server-URL). Nur für angemeldete Admins sichtbar.</p>
      <div style="display:flex;gap:8px;align-items:center;max-width:640px;flex-wrap:wrap">
        <input class="form-control" id="apikey-field" type="password" value="" readonly
               style="font-family:monospace;flex:1;min-width:240px" placeholder="…">
        <button class="btn btn-secondary btn-sm" id="apikey-toggle">Anzeigen</button>
        <button class="btn btn-primary btn-sm" id="apikey-copy">Kopieren</button>
      </div>
    </div>
    <div class="alert alert-info" style="margin-bottom:16px">
      <strong>Hinweis:</strong> Passwörter werden sicher als bcrypt-Hash gespeichert. Vergessene Passwörter können hier zurückgesetzt werden.
    </div>
    <div id="bu-container"><div class="loading">Wird geladen…</div></div>
  `;

  // API-Schlüssel laden + Anzeigen/Kopieren
  (async () => {
    const f = document.getElementById('apikey-field');
    const toggle = document.getElementById('apikey-toggle');
    const copy = document.getElementById('apikey-copy');
    if (!f) return;
    try {
      const k = await api('/kkh/api/web/apikey');
      f.value = k.apiKey || '';
    } catch (e) {
      f.value = '';
      f.placeholder = 'Konnte nicht geladen werden';
    }
    toggle?.addEventListener('click', () => {
      const zeigen = f.type === 'password';
      f.type = zeigen ? 'text' : 'password';
      toggle.textContent = zeigen ? 'Verbergen' : 'Anzeigen';
    });
    copy?.addEventListener('click', async () => {
      if (!f.value) return;
      try {
        await navigator.clipboard.writeText(f.value);
        toast('API-Schlüssel kopiert');
      } catch {
        const alt = f.type; f.type = 'text'; f.select();
        try { document.execCommand('copy'); toast('API-Schlüssel kopiert'); } catch { toast('Kopieren nicht möglich', 'warn'); }
        f.type = alt;
      }
    });
  })();

  async function lade() {
    const container = document.getElementById('bu-container');
    if (!container) return;
    let data;
    try { data = await api('/kkh/api/web/users'); }
    catch (e) { container.innerHTML = `<div class="alert alert-err">${escH(e.message)}</div>`; return; }

    new DataGrid(container, {
      data: data.users || [],
      filterKeys: ['username'],
      columns: [
        { key: 'id', label: '#', sort: true, width: '50px', align: 'right' },
        { key: 'username', label: 'Benutzername', sort: true,
          render: (v) => `<strong>${escH(v)}</strong>` },
        { key: 'created_at', label: 'Erstellt am', sort: true, width: '160px',
          render: (v) => v ? new Date(v).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' }) : '–' },
        { key: '_actions', label: 'Aktionen', width: '230px', align: 'right',
          render: (v, row) => `
            <button class="btn btn-xs btn-secondary bu-rename" data-id="${row.id}" data-name="${escH(row.username)}">✏ Umbenennen</button>
            <button class="btn btn-xs btn-secondary bu-reset" data-id="${row.id}" data-name="${escH(row.username)}">🔑 PW Reset</button>
            <button class="btn btn-xs btn-danger bu-del" data-id="${row.id}" data-name="${escH(row.username)}">🗑</button>
          ` },
      ],
    });

  }

  // Aktionen einmalig als Delegation registrieren (nicht in lade(), sonst
  // stapeln sich bei jedem Neuladen die Listener → mehrfach geöffnete Dialoge).
  document.getElementById('bu-container').addEventListener('click', async (e) => {
    const resetBtn = e.target.closest('.bu-reset');
    const renameBtn = e.target.closest('.bu-rename');
    const delBtn = e.target.closest('.bu-del');

    if (resetBtn) {
      await resetPasswort(resetBtn.dataset.id, resetBtn.dataset.name);
    } else if (renameBtn) {
      await umbenennen(renameBtn.dataset.id, renameBtn.dataset.name);
    } else if (delBtn) {
      const id = delBtn.dataset.id;
      const name = delBtn.dataset.name;
      if (!(await confirm(`Benutzer "${name}" wirklich löschen? Diese Aktion ist nicht rückgängig zu machen.`))) return;
      try {
        await api(`/kkh/api/web/users/${id}`, { method: 'DELETE' });
        toast('Benutzer gelöscht');
        lade();
      } catch (err) { toast(err.message, 'err'); }
    }
  });

  async function resetPasswort(id, name) {
    const res = await modal(`Passwort zurücksetzen: ${name}`, `
      <div class="form-grid">
        <div class="form-group"><label class="form-label">Neues Passwort *</label>
          <input type="password" class="form-control" id="rp-pw1" placeholder="Mindestens 4 Zeichen"></div>
        <div class="form-group"><label class="form-label">Wiederholen *</label>
          <input type="password" class="form-control" id="rp-pw2"></div>
      </div>`,
      [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Zurücksetzen', cls: 'btn-danger', value: 'ok' }]);
    if (!res || res.action !== 'ok') return;
    const pw1 = mf(res, 'rp-pw1');
    const pw2 = mf(res, 'rp-pw2');
    if (!pw1 || pw1.length < 4) { toast('Passwort muss mindestens 4 Zeichen haben', 'err'); return; }
    if (pw1 !== pw2) { toast('Passwörter stimmen nicht überein', 'err'); return; }
    try {
      await api(`/kkh/api/web/users/${id}`, { method: 'PATCH', body: { newPassword: pw1 } });
      toast(`Passwort von "${name}" erfolgreich zurückgesetzt`);
    } catch (e) { toast(e.message, 'err'); }
  }

  async function umbenennen(id, name) {
    const res = await modal(`Benutzer umbenennen: ${name}`, `
      <div class="form-group">
        <label class="form-label">Neuer Benutzername *</label>
        <input class="form-control" id="rn-name" value="${escH(name)}" autocapitalize="none">
      </div>`,
      [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Umbenennen', cls: 'btn-primary', value: 'ok' }]);
    if (!res || res.action !== 'ok') return;
    const newName = mf(res, 'rn-name')?.trim();
    if (!newName || newName === name) return;
    try {
      await api(`/kkh/api/web/users/${id}`, { method: 'PATCH', body: { username: newName } });
      toast('Benutzername geändert');
      lade();
    } catch (e) { toast(e.message, 'err'); }
  }

  document.getElementById('bu-neu').addEventListener('click', async () => {
    const res = await modal('Neuen Benutzer anlegen', `
      <div class="form-grid">
        <div class="form-group full"><label class="form-label">Benutzername *</label>
          <input class="form-control" id="bu-user" autocapitalize="none" placeholder="Kleinbuchstaben, keine Sonderzeichen"></div>
        <div class="form-group"><label class="form-label">Passwort *</label>
          <input type="password" class="form-control" id="bu-pw" placeholder="Mindestens 4 Zeichen"></div>
        <div class="form-group"><label class="form-label">Passwort wiederholen *</label>
          <input type="password" class="form-control" id="bu-pw2"></div>
      </div>`,
      [{ label: 'Abbrechen', value: null, cls: 'btn-secondary' }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
    if (!res || res.action !== 'ok') return;
    const username = mf(res, 'bu-user')?.trim();
    const pw = mf(res, 'bu-pw');
    const pw2 = mf(res, 'bu-pw2');
    if (!username) { toast('Benutzername eingeben', 'err'); return; }
    if (!pw || pw.length < 4) { toast('Passwort mindestens 4 Zeichen', 'err'); return; }
    if (pw !== pw2) { toast('Passwörter stimmen nicht überein', 'err'); return; }
    try {
      await api('/kkh/api/web/users', { method: 'POST', body: { username, password: pw } });
      toast(`Benutzer "${username}" erfolgreich angelegt`);
      lade();
    } catch (e) { toast(e.message, 'err'); }
  });

  await lade();
}
